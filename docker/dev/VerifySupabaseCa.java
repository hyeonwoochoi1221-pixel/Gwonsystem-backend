import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

/** Build-time validation only; no network or application credentials are used. */
public final class VerifySupabaseCa {
    private static final String EXPECTED_SHA256 =
        "807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected one public CA file");
        byte[] pem = Files.readAllBytes(Path.of(args[0]));
        String text = new String(pem, java.nio.charset.StandardCharsets.US_ASCII);
        if (text.contains("PRIVATE KEY")) throw new IllegalArgumentException("Private keys forbidden");
        try (var input = new java.io.ByteArrayInputStream(pem)) {
            var certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
            if (certificates.size() != 1) throw new IllegalArgumentException("Expected exactly one CA");
            var certificate = (X509Certificate) certificates.iterator().next();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(certificate.getEncoded()));
            if (!EXPECTED_SHA256.equals(actual)) throw new IllegalArgumentException("CA fingerprint mismatch");
            certificate.checkValidity();
            if (certificate.getBasicConstraints() < 0
                || !certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal()))
                throw new IllegalArgumentException("Expected self-signed root CA");
            boolean[] usage = certificate.getKeyUsage();
            if (usage != null && (usage.length <= 5 || !usage[5]))
                throw new IllegalArgumentException("CA signing usage missing");
            certificate.verify(certificate.getPublicKey());
            System.out.println("Verified Supabase Root 2021 CA SHA-256: " + actual);
        }
    }
}
