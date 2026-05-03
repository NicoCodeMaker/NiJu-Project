package com.example.niju_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.data.model.ContextModel

class ContextosAdapter(
    private var listaContextos: List<ContextModel>,
    private val onContextoClick: (ContextModel) -> Unit
) : RecyclerView.Adapter<ContextosAdapter.ContextoViewHolder>() {

    fun updateList(newList: List<ContextModel>) {
        this.listaContextos = newList
        notifyDataSetChanged()
    }

    class ContextoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icono: ImageView = itemView.findViewById(R.id.iv_contexto_icono)
        val nombre: TextView = itemView.findViewById(R.id.tv_contexto_nombre)
        val desc: TextView? = itemView.findViewById(R.id.tv_contexto_desc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContextoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contexto, parent, false)
        return ContextoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContextoViewHolder, position: Int) {
        val contexto = listaContextos[position]
        holder.nombre.text = contexto.name
        holder.desc?.text = contexto.description
        
        // Aquí podrías usar Glide o Coil para cargar la iconoUrl
        // Por ahora lo dejamos por defecto
        holder.icono.setImageResource(R.drawable.beach_scene)

        holder.itemView.setOnClickListener {
            onContextoClick(contexto)
        }
    }

    override fun getItemCount(): Int = listaContextos.size
}
