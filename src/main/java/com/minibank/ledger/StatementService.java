package com.minibank.ledger;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minibank.account.Account;
import com.minibank.account.AccountService;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Renders an account statement for a date range (UTC days) as CSV or PDF,
 * with opening and closing balances derived from the ledger.
 */
@Service
public class StatementService {

    public record Statement(Account account, LocalDate from, LocalDate to,
                            BigDecimal openingBalance, BigDecimal closingBalance,
                            List<LedgerEntry> entries) {
    }

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final AccountService accountService;
    private final LedgerRepository ledger;

    public StatementService(AccountService accountService, LedgerRepository ledger) {
        this.accountService = accountService;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public Statement statement(UUID accountId, UUID ownerId, LocalDate from, LocalDate to) {
        Account account = accountService.get(accountId, ownerId);
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<LedgerEntry> entries = ledger
                .findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        accountId, start, end);
        BigDecimal opening = ledger
                .findFirstByAccountIdAndCreatedAtBeforeOrderByCreatedAtDesc(accountId, start)
                .map(LedgerEntry::getBalanceAfter)
                .orElse(BigDecimal.ZERO);
        BigDecimal closing = entries.isEmpty()
                ? opening
                : entries.getLast().getBalanceAfter();
        return new Statement(account, from, to, opening, closing, entries);
    }

    public byte[] toCsv(Statement statement) {
        StringBuilder csv = new StringBuilder("timestamp_utc,type,amount,balance_after,transfer_id\n");
        for (LedgerEntry entry : statement.entries()) {
            csv.append(TIMESTAMP.format(entry.getCreatedAt())).append(',')
                    .append(entry.getType()).append(',')
                    .append(signed(entry)).append(',')
                    .append(entry.getBalanceAfter()).append(',')
                    .append(entry.getTransferId() == null ? "" : entry.getTransferId())
                    .append('\n');
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] toPdf(Statement statement) {
        var out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

        Account account = statement.account();
        document.add(new Paragraph("minibank — Account statement", title));
        document.add(new Paragraph("Holder: %s".formatted(account.getHolderName()), normal));
        document.add(new Paragraph("Account: %s (%s)".formatted(account.getAccountNumber(), account.getCurrency()), normal));
        document.add(new Paragraph("Period: %s to %s".formatted(statement.from(), statement.to()), normal));
        document.add(new Paragraph("Opening balance: %s   Closing balance: %s"
                .formatted(statement.openingBalance(), statement.closingBalance()), bold));
        document.add(new Paragraph(" ", normal));

        PdfPTable table = new PdfPTable(new float[]{3, 2, 2, 2, 4});
        table.setWidthPercentage(100);
        for (String header : new String[]{"Timestamp (UTC)", "Type", "Amount", "Balance", "Transfer"}) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, bold));
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cell);
        }
        for (LedgerEntry entry : statement.entries()) {
            table.addCell(new Paragraph(TIMESTAMP.format(entry.getCreatedAt()), normal));
            table.addCell(new Paragraph(entry.getType().name(), normal));
            table.addCell(new Paragraph(signed(entry).toPlainString(), normal));
            table.addCell(new Paragraph(entry.getBalanceAfter().toPlainString(), normal));
            table.addCell(new Paragraph(
                    entry.getTransferId() == null ? "—" : entry.getTransferId().toString().substring(0, 8), normal));
        }
        document.add(table);
        document.close();
        return out.toByteArray();
    }

    private static BigDecimal signed(LedgerEntry entry) {
        return entry.getType() == EntryType.DEBIT ? entry.getAmount().negate() : entry.getAmount();
    }
}
