import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth';
import { problemMessage } from '../../core/models';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  templateUrl: './login-page.html'
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly mode = signal<'login' | 'register'>('login');
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    displayName: ['']
  });

  switchMode(mode: 'login' | 'register'): void {
    this.mode.set(mode);
    this.error.set(null);
  }

  submit(): void {
    const { email, password, displayName } = this.form.getRawValue();
    if (this.form.controls.email.invalid || this.form.controls.password.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.mode() === 'register' && !displayName.trim()) {
      this.error.set('Display name is required to create an account');
      return;
    }
    this.submitting.set(true);

    const login = () =>
      this.auth.login(email, password).subscribe({
        next: () => this.router.navigate(['/']),
        error: err => {
          this.submitting.set(false);
          this.error.set(problemMessage(err, 'Login failed'));
        }
      });

    if (this.mode() === 'login') {
      login();
    } else {
      this.auth.register(email, password, displayName.trim()).subscribe({
        next: () => login(),
        error: err => {
          this.submitting.set(false);
          this.error.set(problemMessage(err, 'Registration failed'));
        }
      });
    }
  }
}
