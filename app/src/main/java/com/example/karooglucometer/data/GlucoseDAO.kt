/*
This file contains the Database Access Object (DAO) for GlucoseReading. This acts as the bridge
between the database and the rest of the app.
 */

package com.example.karooglucometer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GlucoseDao {
    /*
    "Suspend" is so that database operations are performed on separate threads from the UI. Because
    DB operations can be costly in time, this prevents UI jankiness.
     */

    // insert() adds a GlucoseReading to the database
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: GlucoseReading)

    // getAll() returns a list of GlucoseReadings in descending order by timestamp
    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC")
    suspend fun getAll(): List<GlucoseReading>

    // clear() deletes all entries from the database
    @Query("DELETE FROM glucose_readings")
    suspend fun clear()

    // getRecent() returns the most recent 5 GlucoseReadings
    @Query("SELECT * FROM glucose_readings ORDER BY timestamp DESC LIMIT 5")
    suspend fun getRecent(): List<GlucoseReading>
}
