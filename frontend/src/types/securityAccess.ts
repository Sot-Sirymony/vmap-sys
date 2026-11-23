/** V24 (BRD 24.0.0): sign-in method settings. */

export type TwoFactorScope = 'PRIVILEGED' | 'EVERYONE';

export type OfferedSignInMethods = { password: boolean; google: boolean };

/** How many active non-Admin accounts a method affects (SA-FR-02). */
export type MethodUsage = { accounts: number; onlyWay: number; openSessions: number };

export type SecurityAccess = {
  passwordSignIn: boolean;
  googleSignIn: boolean;
  twoFactorScope: TwoFactorScope;
  /** Google is set up on the server (and step-up mail works). */
  googleAvailable: boolean;
  /** Admins only (D-24-2); roles with "View security log" read. */
  canChange: boolean;
  updatedAt: string;
  updatedBy: string | null;
  password: MethodUsage;
  google: MethodUsage;
};

export type SecurityAccessInput = {
  passwordSignIn: boolean;
  googleSignIn: boolean;
  twoFactorScope: TwoFactorScope;
  reason: string;
};
