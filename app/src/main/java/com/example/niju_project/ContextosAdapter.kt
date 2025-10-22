package com.example.niju_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Modelo de datos para cada contexto
data class Contexto(
    val nombre: String,
    val icono: Int
)

class ContextosAdapter(
    private val listaContextos: List<Contexto>,
    private val onContextoClick: (Contexto) -> Unit
) : RecyclerView.Adapter<ContextosAdapter.ContextoViewHolder>() {

    // Para saber cuál está seleccionado
    private var contextoSeleccionado: Int = RecyclerView.NO_POSITION

    // ViewHolder representa UNA tarjeta (un ítem del RecyclerView)
    class ContextoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icono: ImageView = itemView.findViewById(R.id.iv_contexto_icono)
        val nombre: TextView = itemView.findViewById(R.id.tv_contexto_nombre)
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

        // Fondo dependiendo de si está seleccionado o no
        if (position == contextoSeleccionado) {
            holder.itemView.setBackgroundResource(R.drawable.bg_contexto_selected)
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_contexto_default)
        }

        // Evento de clic
        holder.itemView.setOnClickListener {
            val anterior = contextoSeleccionado
            contextoSeleccionado = position
            notifyItemChanged(anterior)
            notifyItemChanged(contextoSeleccionado)

            onContextoClick(contexto)
        }
    }

    override fun getItemCount(): Int = listaContextos.size
}
