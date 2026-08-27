package com.darbukapractice.app.db

import android.content.Context
import androidx.room.*

@Entity(tableName="practice_sessions")
data class SessionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,@ColumnInfo(name="exercise_id") val exerciseId:Int,val timestamp:Long,val bpm:Int,val durationSeconds:Long)

@Dao interface SessionDao {
 @Query("SELECT * FROM practice_sessions ORDER BY timestamp DESC") suspend fun all():List<SessionEntity>
 @Query("SELECT * FROM practice_sessions WHERE exercise_id=:exerciseId ORDER BY timestamp DESC") suspend fun byExercise(exerciseId:Int):List<SessionEntity>
 @Insert suspend fun insert(e:SessionEntity)
 @Delete suspend fun delete(e:SessionEntity)
}
@Database(entities=[SessionEntity::class],version=1,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){abstract fun sessions():SessionDao
 companion object { @Volatile private var INSTANCE:AppDatabase?=null
  fun get(context:Context)=INSTANCE ?: synchronized(this){INSTANCE ?: Room.databaseBuilder(context,AppDatabase::class.java,"darbuka_practice.db").build().also{INSTANCE=it}}
 }
}
