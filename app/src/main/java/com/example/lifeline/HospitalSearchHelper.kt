package com.example.lifeline

import android.content.Context
import android.location.Geocoder
import java.util.*

object HospitalSearchHelper {

    data class PickupLoc(
        val address:String,
        val lat:Double,
        val lng:Double
    )

    fun findPickupAddress(
        context: Context,
        text:String,
        onSuccess:(PickupLoc)->Unit,
        onFailure:(String)->Unit
    ){
        Thread{
            try{
                val geocoder = Geocoder(context, Locale.getDefault())

                val queries = listOf(
                    text,
                    "$text Pune",
                    "$text Talegaon",
                    "$text Maharashtra"
                )

                for(q in queries){
                    val list = geocoder.getFromLocationName(q,5)
                    if(!list.isNullOrEmpty()){
                        val best = list[0]
                        onSuccess(
                            PickupLoc(
                                best.getAddressLine(0) ?: q,
                                best.latitude,
                                best.longitude
                            )
                        )
                        return@Thread
                    }
                }

                onFailure("Address not found")

            }catch (e:Exception){
                onFailure(e.message ?: "Failed")
            }
        }.start()
    }
}