package com.example.niju_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.data.model.ContextRow

class ContextosAdapter(
    private var contexts: List<ContextRow>,
    private val onClick: (ContextRow) -> Unit
) : RecyclerView.Adapter<ContextosAdapter.ContextoViewHolder>() {

    fun updateList(newList: List<ContextRow>) {
        contexts = newList
        notifyDataSetChanged()
    }

    class ContextoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icono: ImageView = itemView.findViewById(R.id.iv_contexto_icono)
        val nombre: TextView = itemView.findViewById(R.id.tv_contexto_nombre)
        val desc: TextView? = itemView.findViewById(R.id.tv_contexto_desc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContextoViewHolder =
        ContextoViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_contexto, parent, false)
        )

    override fun onBindViewHolder(holder: ContextoViewHolder, position: Int) {
        val contexto = contexts[position]
        holder.nombre.text = contexto.name
        holder.desc?.text = contexto.description
        holder.icono.setImageResource(contextImage(contexto.slug))
        holder.icono.contentDescription = contexto.name
        holder.itemView.setOnClickListener { onClick(contexto) }
    }

    override fun getItemCount(): Int = contexts.size
}
