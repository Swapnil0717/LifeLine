package com.example.lifeline

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlin.math.abs

class DoctorHome : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var gestureDetector: GestureDetector

    private var currentIndex = 0

    private val fragments = listOf(
        DoctorFragment(),
        HistoryFragment(),
        ProfileFragment()
    )

    private val menuIds = listOf(
        R.id.nav_home,
        R.id.nav_appointment,
        R.id.nav_profile
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)

        loadFragment(fragments[currentIndex])

        bottomNav.setOnItemSelectedListener { item ->
            val index = menuIds.indexOf(item.itemId)

            if (index != -1) {
                currentIndex = index
                loadFragment(fragments[currentIndex])
                true
            } else {
                false
            }
        }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {

                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) && abs(diffX) > 100 && abs(velocityX) > 100) {

                    if (diffX < 0) {
                        moveToNextFragment()
                    } else {
                        moveToPreviousFragment()
                    }

                    return true
                }

                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun moveToNextFragment() {
        if (currentIndex < fragments.size - 1) {
            currentIndex++
            bottomNav.selectedItemId = menuIds[currentIndex]
        }
    }

    private fun moveToPreviousFragment() {
        if (currentIndex > 0) {
            currentIndex--
            bottomNav.selectedItemId = menuIds[currentIndex]
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}