package com.example.niju_project
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.R

class ContextosAdapter(
    private val contextos: List<Contexto>,
    private val onClick: (Contexto) -> Unit
) : RecyclerView.Adapter<ContextosAdapter.ContextoViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class ContextoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tv_nombre_contexto)
        val ivIcono: ImageView = itemView.findViewById(R.id.iv_icono_contexto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContextoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contexto, parent, false)
        return ContextoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContextoViewHolder, position: Int) {
        val contexto = contextos[position]

        holder.tvNombre.text = contexto.nombre
        holder.ivIcono.setImageResource(contexto.icono)

        // Cambia el fondo si está seleccionado
        holder.itemView.setBackgroundResource(
            if (position == selectedPosition) R.drawable.bg_contexto_selected
            else R.drawable.bg_contexto_default
        )

        holder.itemView.setOnClickListener {
            val previous = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previous)
            notifyItemChanged(selectedPosition)
            onClick(contexto)
        }
    }

    override fun getItemCount(): Int = contextos.size
}
