package io.getstream.chat.android.core.poc.library.gson

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.getstream.chat.android.core.poc.library.errors.ChatHttpError
import io.getstream.chat.android.core.poc.library.json.ConverterFactory
import io.getstream.chat.android.core.poc.library.json.IgnoreDeserialisation
import io.getstream.chat.android.core.poc.library.json.IgnoreSerialisation
import io.getstream.chat.android.core.poc.library.json.TypeAdapterFactory
import io.getstream.chat.android.core.poc.library.socket.ErrorResponse
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class JsonParserImpl : JsonParser {

    private val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapterFactory(TypeAdapterFactory())
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
            .setPrettyPrinting()
            .addSerializationExclusionStrategy(object : ExclusionStrategy {
                override fun shouldSkipClass(clazz: Class<*>): Boolean {
                    return false
                }

                override fun shouldSkipField(f: FieldAttributes): Boolean {
                    return f.getAnnotation(IgnoreSerialisation::class.java) != null
                }

            })
            .addDeserializationExclusionStrategy(object : ExclusionStrategy {
                override fun shouldSkipClass(clazz: Class<*>): Boolean {
                    return false
                }

                override fun shouldSkipField(f: FieldAttributes): Boolean {
                    return f.getAnnotation(IgnoreDeserialisation::class.java) != null
                }

            })
            .create()
    }

    override fun toJson(any: Any): String {
        return gson.toJson(any)
    }

    override fun <T> fromJson(raw: String, clazz: Class<T>): T {
        return gson.fromJson(raw, clazz)
    }

    override fun toError(okHttpResponse: Response): ChatHttpError {

        var statusCode = -1

        return try {

            statusCode = okHttpResponse.code

            val body = okHttpResponse.peekBody(Long.MAX_VALUE).string()
            val error = toError(body)
            ChatHttpError(error.code, statusCode, error.message)
        } catch (t: Throwable) {
            ChatHttpError(okHttpResponse.code, statusCode, t.message.toString(), t)
        }
    }

    override fun configRetrofit(builder: Retrofit.Builder): Retrofit.Builder {
        return builder
            .addConverterFactory(ConverterFactory(gson))
            .addConverterFactory(GsonConverterFactory.create(gson))
    }

    private fun toError(body: String?): ErrorResponse {

        if (body == null) return ErrorResponse(message = "Body is null")

        return try {
            fromJson(body, ErrorResponse::class.java)
        } catch (e: Throwable) {
            ErrorResponse().apply {
                message = e.message.toString() + " from body: " + body
            }
        }
    }
}