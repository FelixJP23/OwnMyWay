package com.example.ownmyway

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ownmyway.model.SocialAction

class SocialFeedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_social_feed)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarSocial)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        setupFeed()
    }

    private fun setupFeed() {
        val mockData = listOf(
            SocialAction("Artur", "está planejando uma viagem para", "Paris, França", "2h", 0),
            SocialAction("Lucas", "quer visitar", "Jalapão, Tocantins", "5h", 0),
            SocialAction("Maria", "está no meio de uma viagem para", "Lisboa, Portugal", "1d", 0),
            SocialAction("João", "planeja um mochilão por", "Machu Picchu, Peru", "3d", 0),
            SocialAction("Beatriz", "tem interesse em conhecer", "Tóquio, Japão", "1w", 0)
        )

        val rv = findViewById<RecyclerView>(R.id.rvSocialFeed)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = SocialFeedAdapter(mockData)
    }
}
