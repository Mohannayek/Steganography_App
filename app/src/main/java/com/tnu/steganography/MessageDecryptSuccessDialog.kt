package com.tnu.steganography

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.tnu.steganography.databinding.DialogMessageDecryptSuccessBinding

class MessageDecryptSuccessDialog(private val decryptedMessage: String) : DialogFragment() {

    private var _binding: DialogMessageDecryptSuccessBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            // Optional: Make the dialog non-cancelable by touching outside
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMessageDecryptSuccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.secretMessageTextView.text = decryptedMessage

        binding.goBackButton.setOnClickListener {
            // You might want to navigate back to the main screen or just dismiss
            dismiss()
            requireActivity().onBackPressedDispatcher.onBackPressed() // Go back from decode screen
        }

        binding.okButton.setOnClickListener {
            dismiss() // Just dismiss the dialog
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}