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
        val ivRestaurante: ImageView = itemView.findViewById(R.id.iv_restaurante)
        val tvRestaurante: TextView = itemView.findViewById(R.id.tv_restaurante)
        val ivSupermercado: ImageView = itemView.findViewById(R.id.iv_supermercado)
        val tvSupermercado: TextView = itemView.findViewById(R.id.tv_supermercado)
        val ivAeropuerto: ImageView = itemView.findViewById(R.id.iv_aeropuerto)
        val tvAeropuerto: TextView = itemView.findViewById(R.id.tv_aeropuerto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContextoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contexto, parent, false)
        return ContextoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContextoViewHolder, position: Int) {
        val contexto = listaContextos[position]
        // Según el nombre del contexto, mostramos uno u otro grupo
        when (contexto.nombre.lowercase()) {
            "restaurante" -> {
                holder.tvRestaurante.text = contexto.nombre
                holder.ivRestaurante.setImageResource(contexto.icono)
            }
            "supermercado" -> {
                holder.tvSupermercado.text = contexto.nombre
                holder.ivSupermercado.setImageResource(contexto.icono)
            }
            "aeropuerto" -> {
                holder.tvAeropuerto.text = contexto.nombre
                holder.ivAeropuerto.setImageResource(contexto.icono)
            }
        }

    }

    override fun getItemCount(): Int = listaContextos.size
}
