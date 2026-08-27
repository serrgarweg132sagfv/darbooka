package com.darbukapractice.app.utils

object Jalali {
 fun format(epochMillis:Long):String {
  val d=java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
  val (jy,jm,jday)=gregorianToJalali(d.year,d.monthValue,d.dayOfMonth)
  return "%04d/%02d/%02d".format(jy,jm,jday)
 }
 private fun gregorianToJalali(gy0:Int,gm0:Int,gd:Int):Triple<Int,Int,Int>{
  var gy=gy0-1600; val gm=gm0-1; val gd0=gd-1
  val gdm=intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
  var gdn=365*gy+(gy+3)/4-(gy+99)/100+(gy+399)/400
  for(i in 0 until gm) gdn+=gdm[i]
  if(gm>1 && (gy0%4==0 && gy0%100!=0 || gy0%400==0)) gdn++
  gdn+=gd0
  var jdn=gdn-79; val jnp=jdn/12053; jdn%=12053
  var jy=979+33*jnp+4*(jdn/1461); jdn%=1461
  if(jdn>=366){jy+=(jdn-1)/365; jdn=(jdn-1)%365}
  val jm=if(jdn<186)1+jdn/31 else 7+(jdn-186)/30
  val jd=1+if(jdn<186) jdn%31 else (jdn-186)%30
  return Triple(jy,jm,jd)
 }
}
