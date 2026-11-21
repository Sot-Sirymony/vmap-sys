-- V14 (BRD 14.0.0, GA-FR-25, D-14-3): an account created with Google has a
-- random password hash nobody knows. This column says whether the account has
-- a password its owner chose, so "change password" and "delete my account" can
-- use the V20 email code instead. Every existing account has one.
ALTER TABLE app_users ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT TRUE;
