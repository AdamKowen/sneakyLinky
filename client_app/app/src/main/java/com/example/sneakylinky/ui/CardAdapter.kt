package com.example.sneakylinky.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.sneakylinky.R



class CardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CARD_1 = 0
        private const val TYPE_CARD_2 = 1
        private const val TYPE_CARD_3 = 2
    }

    override fun getItemCount(): Int = 3

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> TYPE_CARD_1
        1 -> TYPE_CARD_2
        else -> TYPE_CARD_3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_CARD_1 -> {
                val view = inflater.inflate(R.layout.link_check_card, parent, false)
                Card1ViewHolder(view)
            }
            TYPE_CARD_2 -> {
                val view = inflater.inflate(R.layout.browser_pick_card, parent, false)
                Card2ViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.settings_card, parent, false)
                Card3ViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // אפשר לעדכן כאן לוגיקה אם תרצה בעתיד
        when (holder) {
            is Card1ViewHolder -> {
                // דוגמה: תגובה ללחיצה על כפתור
                holder.checkButton.setOnClickListener {
                    val url = holder.editText.text.toString()
                    Toast.makeText(holder.itemView.context, "בדקתי: $url", Toast.LENGTH_SHORT).show()
                }
            }

            is Card2ViewHolder -> {
                // בהמשך נכניס כאן את הקוד לרשימת דפדפנים
            }

            is Card3ViewHolder -> {
                // כרטיס ריק או משהו אחר
            }
        }
    }

    class Card1ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val editText: EditText = itemView.findViewById(R.id.editTextUrl)
        val checkButton: Button = itemView.findViewById(R.id.checkButton)
    }

    class Card2ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // דוגמה: טקסט או RecyclerView
    }

    class Card3ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ריק או טקסט
    }
}
