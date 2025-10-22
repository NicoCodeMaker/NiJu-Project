package com.example.niju_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Contexto(
    val nombre: String,
    val icono: Int
)

class ContextosAdapter(
    private val listaContextos: List<Contexto>,
    private val onContextoClick: (Contexto) -> Unit
) : RecyclerView.Adapter<ContextosAdapter.ContextoViewHolder>() {

    class ContextoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icono: ImageView = itemView.findViewById(R.id.iv_restaurante)
        val nombre: TextView = itemView.findViewById(R.id.tv_restaurante)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContextoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contexto, parent, false)
        return ContextoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContextoViewHolder, position: Int) {
        val contexto = listaContextos[position]
        holder.nombre.text = contexto.nombre
        holder.icono.setImageResource(contexto.icono)

        // Evento de clic para cambiar color del borde, progreso, etc.
        holder.itemView.setOnClickListener {
            onContextoClick(contexto)
            holder.itemView.setBackgroundResource(R.drawable.bg_contexto_selected)
        }
    }

    override fun getItemCount(): Int = listaContextos.size
}
