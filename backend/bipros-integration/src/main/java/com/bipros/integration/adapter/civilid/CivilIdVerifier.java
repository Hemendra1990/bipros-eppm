package com.bipros.integration.adapter.civilid;

/**
 * Validates a worker's national/civil/passport identifier against an external authority
 * (e.g. Oman e-Gate / ROP). Default implementation is a permissive mock.
 */
public interface CivilIdVerifier {

    CivilIdVerificationResult verify(String civilId, String nationality);

    record CivilIdVerificationResult(
            boolean verified,
            String fullName,
            String nationality,
            String tradeCertification,
            boolean expired,
            String message
    ) {
        public static CivilIdVerificationResult ok(String fullName, String nationality) {
            return new CivilIdVerificationResult(true, fullName, nationality, null, false, "verified");
        }

        public static CivilIdVerificationResult unverified(String message) {
            return new CivilIdVerificationResult(false, null, null, null, false, message);
        }
    }
}
