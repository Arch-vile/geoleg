package com.nakoradio.geoleg.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class Cryptor( @Value("\${secret_key}") val SECRET: String){

    fun aesEncrypt(v:String) = AES256.encrypt(v, SECRET)
    fun aesDecrypt(v:String) = AES256.decrypt(v, SECRET)
}

private object AES256{
    private val encorder = Base64.getEncoder()
    private val decorder = Base64.getDecoder()
    private fun cipher(opmode:Int, secretKey:String):Cipher{
        println(secretKey)
        if(secretKey.length != 32) throw RuntimeException("SecretKey length is not 32 chars")
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val sk = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "AES")
        val iv = IvParameterSpec(secretKey.substring(0, 16).toByteArray(Charsets.UTF_8))
        c.init(opmode, sk, iv)
        return c
    }
    fun encrypt(str:String, secretKey:String):String{
        val encrypted = cipher(Cipher.ENCRYPT_MODE, secretKey).doFinal(str.toByteArray(Charsets.UTF_8))
        return String(encorder.encode(encrypted))
    }
    fun decrypt(str:String, secretKey:String):String{
        val byteStr = decorder.decode(str.toByteArray(Charsets.UTF_8))
        return String(cipher(Cipher.DECRYPT_MODE, secretKey).doFinal(byteStr))
    }
}