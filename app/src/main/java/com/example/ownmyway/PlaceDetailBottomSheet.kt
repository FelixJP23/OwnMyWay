package com.example.ownmyway

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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

    // ── Make the bottom-sheet background fully transparent so the map
    //    shows through the glass card effect. ──
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dlg ->
            val d = dlg as BottomSheetDialog

            // 1. Make the default sheet container transparent
            val bottomSheet = d.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)

            // 2. Expand the sheet so everything is visible
            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_place_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Make the dialog window transparent (removes white scrim behind the sheet)
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.25f)   // very subtle dim so the map stays visible
        }

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

        // ── Photo carousel ──
        val viewPager    = view.findViewById<ViewPager2>(R.id.viewPagerPhotos)
        val tabIndicator = view.findViewById<TabLayout>(R.id.tabIndicator)

        if (photoUrls.isNotEmpty()) {
            viewPager.adapter = PlacePhotoAdapter(photoUrls)

            // Fix: allow horizontal swiping inside the bottom-sheet
            viewPager.getChildAt(0)?.isNestedScrollingEnabled = false

            // Dots will automatically match the number of photos in the adapter
            TabLayoutMediator(tabIndicator, viewPager) { _, _ -> }.attach()

            viewPager.visibility    = View.VISIBLE
            tabIndicator.visibility = if (photoUrls.size > 1) View.VISIBLE else View.GONE
        } else {
            viewPager.visibility    = View.GONE
            tabIndicator.visibility = View.GONE
        }

        // ── Place name ──
        view.findViewById<TextView>(R.id.tvPlaceName).text = name

        // ── Rating pill (overlaid on photo, top-right) ──
        view.findViewById<TextView>(R.id.tvRating).apply {
            if (rating > 0) {
                text = "⭐ ${"%.1f".format(rating)}"
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        // ── Estimated cost (bottom-left, large) ──
        view.findViewById<TextView>(R.id.tvEstimatedCost).text = estimatedCost

        // ── Price tag pill (overlaid on photo, top-left) ──
        view.findViewById<TextView>(R.id.tvPriceTag).text = priceTag

        // ── Address ──
        view.findViewById<TextView>(R.id.tvAddress).text =
            if (address.isNotBlank()) "📍 $address" else ""

        // ── Open-status pill (next to the name) ──
        view.findViewById<TextView>(R.id.tvOpenStatus).apply {
            when (isOpen) {
                true  -> {
                    text = "Aberto"
                    setTextColor(0xFF1B5E20.toInt())
                    visibility = View.VISIBLE
                }
                false -> {
                    text = "Fechado"
                    setTextColor(0xFFC62828.toInt())
                    visibility = View.VISIBLE
                }
                null  -> visibility = View.GONE
            }
        }

        // ── "Take me there" button → sends route_request back to the map ──
        view.findViewById<Button>(R.id.btnTakeMe).setOnClickListener {
            val bundle = Bundle().apply {
                putDouble("lat", lat)
                putDouble("lng", lng)
                putString("name", name)
            }
            parentFragmentManager.setFragmentResult("route_request", bundle)
            dismiss()
        }
    }
}
