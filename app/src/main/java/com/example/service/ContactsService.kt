package com.example.service

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactResultData(
    val name: String,
    val phoneNumber: String,
    val email: String? = null
)

class ContactsService(private val context: Context) {

    suspend fun getContactFromUri(contactUri: Uri): Result<ContactResultData> = withContext(Dispatchers.IO) {
        try {
            var name = ""
            var phoneNumber = ""
            var contactId = ""

            val cursor = context.contentResolver.query(
                contactUri,
                null,
                null,
                null,
                null
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = c.getString(nameIndex) ?: ""
                    }

                    // If phone URI directly
                    val phoneIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (phoneIndex != -1) {
                        phoneNumber = c.getString(phoneIndex) ?: ""
                    }

                    val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                    if (idIndex != -1) {
                        contactId = c.getString(idIndex) ?: ""
                    }
                }
            }

            // If phone number not in direct query, query Phone table with contactId
            if (phoneNumber.isBlank() && contactId.isNotBlank()) {
                val phonesCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )
                phonesCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIdx != -1) {
                            phoneNumber = pc.getString(numIdx) ?: ""
                        }
                    }
                }
            }

            if (name.isBlank() && phoneNumber.isBlank()) {
                return@withContext Result.failure(Exception("لم يتم العثور على تفاصيل جهة الاتصال المحددة"))
            }

            Result.success(
                ContactResultData(
                    name = name.ifBlank { "جهة اتصال" },
                    phoneNumber = phoneNumber.ifBlank { "غير متوفر" }
                )
            )
        } catch (e: Exception) {
            Log.e("ContactsService", "Failed reading contact", e)
            Result.failure(e)
        }
    }
}
