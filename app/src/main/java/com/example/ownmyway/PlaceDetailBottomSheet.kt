package com.example.ownmyway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class PlaceDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(
            name: String,
            rating: Double,
            address: String,
            isOpen: Boolean?,
            photoUrls: ArrayList<String>,
            lat: Double,
            lng: Double,
            estimatedCost: String = "R$ ~60",
            priceTag: String = "$$"
        ) = PlaceDetailBottomSheet().apply {
            arguments = Bundle().apply {
                putString("name", name)
                putDouble("rating", rating)
                putString("address", address)
                putDouble("lat", lat)
                putDouble("lng", lng)
                putStringArrayList("photoUrls", photoUrls)
                putString("estimatedCost", estimatedCost)
                putString("priceTag", priceTag)
                if (isOpen != null) putBoolean("isOpen", isOpen)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_place_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: return

        val name          = args.getString("name", "")
        val rating        = args.getDouble("rating", 0.0)
        val address       = args.getString("address", "")
        val isOpen        = if (args.containsKey("isOpen")) args.getBoolean("isOpen") else null
        val photoUrls     = args.getStringArrayList("photoUrls") ?: arrayListOf()
        val lat           = args.getDouble("lat")
        val lng           = args.getDouble("lng")
        val estimatedCost = args.getString("estimatedCost", "R$ ~60")
        val priceTag      = args.getString("priceTag", "$$")

        val viewPager    = view.findViewById<ViewPager2>(R.id.viewPagerPhotos)
        val tabIndicator = view.findViewById<TabLayout>(R.id.tabIndicator)

        if (photoUrls.isNotEmpty()) {
            viewPager.adapter = PlacePhotoAdapter(photoUrls)
            TabLayoutMediator(tabIndicator, viewPager) { _, _ -> }.attach()
            viewPager.visibility    = View.VISIBLE
            tabIndicator.visibility = View.VISIBLE
        } else {
            viewPager.visibility    = View.GONE
            tabIndicator.visibility = View.GONE
        }

        view.findViewById<TextView>(R.id.tvPlaceName).text = name
        view.findViewById<TextView>(R.id.tvRating).text =
            if (rating > 0) "⭐ ${"%.1f".format(rating)}" else "No ratings yet"

        view.findViewById<TextView>(R.id.tvEstimatedCost).text = estimatedCost
        view.findViewById<TextView>(R.id.tvPriceTag).text = priceTag

        view.findViewById<TextView>(R.id.tvAddress).text =
            if (address.isNotBlank()) "📍 $address" else ""

        view.findViewById<TextView>(R.id.tvOpenStatus).apply {
            when (isOpen) {
                true  -> { text = "✅ Open now"; setTextColor(0xFF2E7D32.toInt()) }
                false -> { text = "🔴 Closed";   setTextColor(0xFFC62828.toInt()) }
                null  -> visibility = View.GONE
            }
        }

        view.findViewById<Button>(R.id.btnTakeMe).setOnClickListener {
            val bundle = Bundle().apply {
                putDouble("lat", lat); putDouble("lng", lng); putString("name", name)
            }
            parentFragmentManager.setFragmentResult("route_request", bundle)
            dismiss()
        }
    }
}
