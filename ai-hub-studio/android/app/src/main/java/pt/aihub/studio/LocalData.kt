package pt.aihub.studio

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials stay in encrypted app-private storage; the key never leaves Android Keystore. */
class Vault(context: Context) {
    private val prefs = context.getSharedPreferences("secure", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey("aihub", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("aihub", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun set(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)).apply()
    }
    fun get(name: String): String {
        val source = prefs.getString(name, null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(source, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
                String(doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8)
            }
        }.getOrDefault("")
    }
}

class LocalData(context: Context): SQLiteOpenHelper(context, "aihub.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,kind TEXT,body TEXT,updated INTEGER)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun save(kind: String, obj: JSONObject, id: String = java.util.UUID.randomUUID().toString()): String {
        val body = JSONObject(obj.toString()).put("id",id)
        val values = ContentValues().apply { put("id",id); put("kind",kind); put("body",body.toString()); put("updated",System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("records",null,values,SQLiteDatabase.CONFLICT_REPLACE)
        return id
    }
    fun list(kind: String, query: String = ""): List<JSONObject> = readableDatabase.query("records",arrayOf("body"),"kind=?",arrayOf(kind),null,null,"updated DESC").use { cursor ->
        buildList { while(cursor.moveToNext()) { val text = cursor.getString(0); if (text.contains(query,true)) add(JSONObject(text)) } }
    }
    fun delete(id: String) { writableDatabase.delete("records","id=?",arrayOf(id)) }
    fun clear() { writableDatabase.delete("records",null,null) }
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
