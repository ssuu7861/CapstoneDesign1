package com.example.adminapplication

data class Rider(
    val id : Int,
    val ip : String,
    val port : Int,
    var timeout : Boolean
)
