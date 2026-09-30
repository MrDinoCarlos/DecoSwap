package es.mrdino.decoswap.deployment;

public enum TransactionState {
  PREPARED,
  APPLYING,
  ACTIVE,
  RESTORING,
  COMMITTED,
  FAILED,
  RECOVERY_REQUIRED
}
