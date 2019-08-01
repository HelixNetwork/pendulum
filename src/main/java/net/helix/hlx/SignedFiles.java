package net.helix.hlx;

import net.helix.hlx.crypto.*;
import net.helix.hlx.model.Hash;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;

import java.io.*;
import java.util.Arrays;

public class SignedFiles {

    public static boolean isFileSignatureValid(String filename, String signatureFilename, String publicKey, int depth, int index) throws IOException {
        byte[] signature = digestFile(filename, SpongeFactory.create(SpongeFactory.Mode.S256));
        return validateSignature(signatureFilename, publicKey, depth, index, signature);
    }

    private static boolean validateSignature(String signatureFilename, String publicKey, int depth, int index, byte[] digest) throws IOException {
        //validate signature
        SpongeFactory.Mode mode = SpongeFactory.Mode.S256;
        int security = 1;
        byte[] digests = new byte[0];
        byte[] bundle = Winternitz.normalizedBundle(digest);
        byte[] root;
        int i;

        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(signatureFilename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(signatureFilename) : new InputStreamReader(inputStream))) {

            String line;
            for (i = 0; i < security && (line = reader.readLine()) != null; i++) {
                byte[] lineBytes = Hex.decode(line);
                byte[] bundleFragment = Arrays.copyOfRange(bundle, i * 16, (i + 1) * 16); //todo size
                byte[] winternitzDigest = Winternitz.digest(mode, bundleFragment, lineBytes);
                digests = ArrayUtils.addAll(digests, winternitzDigest);
            }

            if ((line = reader.readLine()) != null) {
                byte[] lineBytes = Hex.decode(line);
                root = Merkle.getMerkleRoot(mode, Winternitz.address(mode, digests), lineBytes, 0, index, depth);

            } else {
                root = Winternitz.address(mode, digests);
            }
            byte[] pubkeyBytes = Hex.decode(publicKey);
            return Arrays.equals(pubkeyBytes, root); // valid
        }
    }

    private static byte[] digestFile(String filename, Sponge sha3) throws IOException {
        try (InputStream inputStream = SignedFiles.class.getResourceAsStream(filename);
             BufferedReader reader = new BufferedReader((inputStream == null)
                 ? new FileReader(filename) : new InputStreamReader(inputStream))) {
            // building snapshot message
            StringBuilder sb = new StringBuilder();
            reader.lines().forEach(line -> {
                String hex = line; // can return a null
                if (hex == null) {
                    throw new IllegalArgumentException("BYTES ARE NULL. INPUT= '" + line + "'");
                }
                sb.append(hex);
            });
            // pad snapshot message to a multiple of 32 and convert into bytes
            byte[] messageBytes = sb.toString().getBytes();
            if (messageBytes.length == 0){
                messageBytes = Hash.NULL_HASH.bytes();
            }
            int requiredLength = (int) Math.ceil(messageBytes.length / 32.0) * 32;
            byte[] finalizedMessage = Merkle.padding(messageBytes, requiredLength);
            // crypto snapshot message
            sha3.absorb(finalizedMessage, 0, finalizedMessage.length);
            byte[] signature = new byte[Sha3.HASH_LENGTH];
            sha3.squeeze(signature, 0, Sha3.HASH_LENGTH);
            return signature;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}