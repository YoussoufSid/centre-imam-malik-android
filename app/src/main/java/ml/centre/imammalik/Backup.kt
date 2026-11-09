package ml.centre.imammalik

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/** Converts Firestore values to JSON and back, keeping timestamps as {"__ts": millis}. */
object BackupCodec {
    fun encode(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Timestamp -> JSONObject().put("__ts", value.toDate().time)
        is Date -> JSONObject().put("__ts", value.time)
        is Map<*, *> -> JSONObject().also { o -> value.forEach { (k, v) -> o.put(k.toString(), encode(v)) } }
        is List<*> -> JSONArray().also { a -> value.forEach { a.put(encode(it)) } }
        is Number, is Boolean, is String -> value
        else -> value.toString()
    }

    fun decode(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject ->
            if (value.length() == 1 && value.has("__ts")) Date(value.getLong("__ts"))
            else value.keys().asSequence().associateWith { decode(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { decode(value.get(it)) }
        else -> value
    }
}
