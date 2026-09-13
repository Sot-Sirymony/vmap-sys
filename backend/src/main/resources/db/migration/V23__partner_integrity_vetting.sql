-- FR-50: seven integrity-and-reliability checks, a risk-override note, a
-- primary-motivator field, and a one-time vetted_at stamp on Partner. All
-- additive and nullable; BR-39 only gates a FINANCIAL/TECHNICAL partner's
-- first move to ACTIVE (see PartnerService.prepareForActive).
ALTER TABLE partners ADD COLUMN flag_dishonesty BOOLEAN;
ALTER TABLE partners ADD COLUMN flag_anger BOOLEAN;
ALTER TABLE partners ADD COLUMN flag_poor_judgment BOOLEAN;
ALTER TABLE partners ADD COLUMN flag_outsized_reward BOOLEAN;
ALTER TABLE partners ADD COLUMN flag_flattery_pressure BOOLEAN;
ALTER TABLE partners ADD COLUMN flag_gossip BOOLEAN;
ALTER TABLE partners ADD COLUMN flag_disregard_boundaries BOOLEAN;
ALTER TABLE partners ADD COLUMN risk_override_note VARCHAR(1000);
ALTER TABLE partners ADD COLUMN primary_motivator VARCHAR(20);
ALTER TABLE partners ADD COLUMN vetted_at TIMESTAMP;
