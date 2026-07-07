package com.beacon.admin.repository

import com.google.firebase.database.FirebaseDatabase

object RealtimeLocationRepository {
    private const val DB_URL = "https://gen-lang-client-0281237877-default-rtdb.asia-southeast1.firebasedatabase.app/"
    
    fun getInstance(): FirebaseDatabase {
        return FirebaseDatabase.getInstance(DB_URL)
    }
}
