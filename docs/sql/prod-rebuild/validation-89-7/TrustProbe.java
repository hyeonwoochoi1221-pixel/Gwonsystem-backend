import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.HexFormat;
public class TrustProbe {
  public static void main(String[] args) throws Exception {
    var store=KeyStore.getInstance("JKS");
    try(var in=Files.newInputStream(Path.of(args[0]))) { store.load(in,"changeit".toCharArray()); }
    var cert=(X509Certificate)store.getCertificate("gwon-supabase-root-2021");
    if(cert==null)throw new AssertionError("CA alias absent");
    var hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
    if(!hash.equals("807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa"))
      throw new AssertionError("CA mismatch");
    cert.checkValidity();cert.verify(cert.getPublicKey());
    if(cert.getBasicConstraints()<0)throw new AssertionError("Not a CA");
    System.out.println("PASS imported CA fingerprint, validity, signature and CA constraints");
    // Corrupt a COPY of the public DER, leaving the source certificate unchanged.
    byte[] bad=cert.getEncoded();bad[bad.length-1]^=1;
    String pem="-----BEGIN CERTIFICATE-----\n"+java.util.Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bad)+"\n-----END CERTIFICATE-----\n";
    Files.writeString(Path.of(args[1]),pem);
  }
}
