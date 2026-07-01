export interface Account {
  id: string;
  accountNumber: string;
  holderName: string;
  currency: string;
  balance: number;
  createdAt: string;
}

export interface LedgerEntry {
  id: string;
  transferId: string | null;
  type: 'DEBIT' | 'CREDIT';
  amount: number;
  balanceAfter: number;
  createdAt: string;
}

export interface Transfer {
  id: string;
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  currency: string;
  description: string | null;
  createdAt: string;
}

export interface OpenAccountRequest {
  holderName: string;
  currency: string;
}

export interface TransferRequest {
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  description?: string;
}

/** RFC 7807 problem detail as produced by the backend. */
export interface ProblemDetail {
  status: number;
  detail?: string;
  errors?: Record<string, string>;
}

export function problemMessage(err: unknown, fallback: string): string {
  const problem = (err as { error?: ProblemDetail })?.error;
  if (problem?.errors) {
    return Object.entries(problem.errors)
      .map(([field, message]) => `${field}: ${message}`)
      .join('; ');
  }
  return problem?.detail ?? fallback;
}
