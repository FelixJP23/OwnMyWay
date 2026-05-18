package com.example.ownmyway

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class FilterBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_filter, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chipGroupTraveler = view.findViewById<ChipGroup>(R.id.chipGroupTraveler)
        val chipGroupGeneral  = view.findViewById<ChipGroup>(R.id.chipGroupGeneral)

        PlaceCategory.values()
            .filter { it.group == PlaceCategory.Group.TRAVELER }
            .forEach { addChip(chipGroupTraveler, it) }

        PlaceCategory.values()
            .filter { it.group == PlaceCategory.Group.GENERAL }
            .forEach { addChip(chipGroupGeneral, it) }

        view.findViewById<Button>(R.id.btnShowMe).setOnClickListener {
            val selected = mutableListOf<String>()
            collectSelected(chipGroupTraveler, selected)
            collectSelected(chipGroupGeneral, selected)

            if (selected.isEmpty()) { dismiss(); return@setOnClickListener }

            val bundle = Bundle().apply {
                putStringArrayList("categories", ArrayList(selected))
            }
            parentFragmentManager.setFragmentResult("filter_result", bundle)
            dismiss()
        }
    }

    private fun addChip(group: ChipGroup, category: PlaceCategory) {
        val chip = Chip(requireContext()).apply {
            text = "${category.emoji} ${category.displayName}"
            isCheckable = true
            chipBackgroundColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(
                    ContextCompat.getColor(requireContext(), R.color.omw_purple_main),
                    ContextCompat.getColor(requireContext(), R.color.omw_input_background_alt)
                )
            )
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(
                        ContextCompat.getColor(requireContext(), R.color.omw_on_primary),
                        ContextCompat.getColor(requireContext(), R.color.omw_purple_main)
                    )
                )
            )
            chipStrokeWidth = 0f
            isCheckedIconVisible = false
            tag = category.name
            textSize = 13f
        }
        group.addView(chip)
    }

    private fun collectSelected(group: ChipGroup, result: MutableList<String>) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) result.add(chip.tag as String)
        }
    }
}
