package com.example.ownmyway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.ownmyway.network.GeminiResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GeminiResultBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_DESC = "description"
        private const val ARG_FACT1 = "fact1"
        private const val ARG_FACT2 = "fact2"
        private const val ARG_CATEGORY = "category"

        fun newInstance(result: GeminiResult) = GeminiResultBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_NAME, result.name)
                putString(ARG_DESC, result.description)
                putString(ARG_FACT1, result.fact1)
                putString(ARG_FACT2, result.fact2)
                putString(ARG_CATEGORY, result.category)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_gemini_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvCategory).text = arguments?.getString(ARG_CATEGORY) ?: ""
        view.findViewById<TextView>(R.id.tvName).text = arguments?.getString(ARG_NAME) ?: ""
        view.findViewById<TextView>(R.id.tvDescription).text = arguments?.getString(ARG_DESC) ?: ""
        view.findViewById<TextView>(R.id.tvFact1).text = "💡 ${arguments?.getString(ARG_FACT1) ?: ""}"
        view.findViewById<TextView>(R.id.tvFact2).text = "💡 ${arguments?.getString(ARG_FACT2) ?: ""}"
    }
}
