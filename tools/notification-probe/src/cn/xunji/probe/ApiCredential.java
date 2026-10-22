package cn.xunji.probe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ApiCredential {
    private static final String ALIAS = "xunji.deepseek.demo.key";
    private final SharedPreferences prefs;
    ApiCredential(Context context) { this(context,"api-credential"); }
    ApiCredential(Context context,String name) { prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
    }
    void save(String secret) throws Exception {
        if (secret.length() < 10 || secret.length() > 300 || secret.contains("\n") || secret.contains("\r")) throw new IllegalArgumentException("请检查密钥格式");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("value", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply();
    }
    String get() throws Exception {
        String value = prefs.getString("value", ""); if (value.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    boolean configured() { return !prefs.getString("value", "").isEmpty(); }
    void clear() { prefs.edit().clear().apply(); }
}
