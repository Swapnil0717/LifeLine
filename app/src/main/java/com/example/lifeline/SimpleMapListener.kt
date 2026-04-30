package com.example.lifeline

import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent

class SimpleMapListener(
    private val onMove: () -> Unit
) : MapListener {

    override fun onScroll(event: ScrollEvent?): Boolean {
        onMove()
        return true
    }

    override fun onZoom(event: ZoomEvent?): Boolean {
        onMove()
        return true
    }
}