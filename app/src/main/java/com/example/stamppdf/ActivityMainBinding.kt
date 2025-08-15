package com.example.stamppdf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ActivityMainBinding private constructor(
    private val rootView: View,
    val btnSelectDocument: MaterialButton,
    val btnSelectStamp: MaterialButton,
    val btnApplyStamp: MaterialButton,
    val btnReset: MaterialButton,
    val btnSavePDF: MaterialButton,
    val tvDocumentInfo: TextView,
    val tvStampInfo: TextView,
    val ivStampPreview: ImageView,
    val ivDocumentPreview: ImageView,
    val ivStampOverlay: ImageView,
    val tvPreviewPlaceholder: TextView,
    val seekBarTransparency: SeekBar,
    val tvTransparencyValue: TextView,
    val rgPosition: RadioGroup,
    val rbTopLeft: RadioButton,
    val rbTopRight: RadioButton,
    val rbBottomLeft: RadioButton,
    val rbBottomRight: RadioButton,
    val rbCenter: RadioButton
) {
    val root: View = rootView

    companion object {
        fun inflate(inflater: LayoutInflater): ActivityMainBinding {
            return inflate(inflater, null, false)
        }

        fun inflate(inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean): ActivityMainBinding {
            val root = inflater.inflate(R.layout.activity_main, parent, attachToParent)
            return bind(root)
        }

        fun bind(root: View): ActivityMainBinding {
            return ActivityMainBinding(
                root,
                root.findViewById(R.id.btnSelectDocument),
                root.findViewById(R.id.btnSelectStamp),
                root.findViewById(R.id.btnApplyStamp),
                root.findViewById(R.id.btnReset),
                root.findViewById(R.id.btnSavePDF),
                root.findViewById(R.id.tvDocumentInfo),
                root.findViewById(R.id.tvStampInfo),
                root.findViewById(R.id.ivStampPreview),
                root.findViewById(R.id.ivDocumentPreview),
                root.findViewById(R.id.ivStampOverlay),
                root.findViewById(R.id.tvPreviewPlaceholder),
                root.findViewById(R.id.seekBarTransparency),
                root.findViewById(R.id.tvTransparencyValue),
                root.findViewById(R.id.rgPosition),
                root.findViewById(R.id.rbTopLeft),
                root.findViewById(R.id.rbTopRight),
                root.findViewById(R.id.rbBottomLeft),
                root.findViewById(R.id.rbBottomRight),
                root.findViewById(R.id.rbCenter)
            )
        }
    }
}