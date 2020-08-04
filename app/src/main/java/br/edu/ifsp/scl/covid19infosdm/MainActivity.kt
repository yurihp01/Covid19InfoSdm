package br.edu.ifsp.scl.covid19infosdm

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.Observer
import br.edu.ifsp.scl.covid19infosdm.model.dataclass.ByCountryResponseList
import br.edu.ifsp.scl.covid19infosdm.model.dataclass.ByCountryResponseListItem
import br.edu.ifsp.scl.covid19infosdm.model.dataclass.DayOneResponseList
import br.edu.ifsp.scl.covid19infosdm.model.dataclass.DayOneResponseListItem
import br.edu.ifsp.scl.covid19infosdm.viewmodel.Covid19ViewModel
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter
import com.jjoe64.graphview.helper.StaticLabelsFormatter
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: Covid19ViewModel
    private lateinit var countryAdapter: ArrayAdapter<String>
    private lateinit var countryNameSlugMap: MutableMap<String, String>

    // preenchera o spinner de informacao / classe para os servicos que serao acessados:
    private enum class Information(val type: String){
        DAY_ONE("Day one"),
        BY_COUNTRY("By country")
    }

    // preenchera o spinner de status / classe para o status que sera buscado no servico:
    private enum class Status(val type: String){
        CONFIRMED("Confirmed"),
        RECOVERED("Recovered"),
        DEATHS("Deaths")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = Covid19ViewModel(this)

        countryAdapterInit()

        informationAdapterInit()

        statusAdapterInit()
    }

    fun onRetrieveClick(view: View){
        when (spinnerInfo.selectedItem.toString()) {
            Information.DAY_ONE.type -> { fetchDayOne() }
            Information.BY_COUNTRY.type -> { fetchByCountry() }
        }
        gvResultado.gridLabelRenderer.resetStyles()
        gvResultado.clearSecondScale()
        gvResultado.removeAllSeries()

    }

    private fun countryAdapterInit(){
        // populado pelo web service
        countryAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        countryNameSlugMap = mutableMapOf()

        spinnerPais.adapter = countryAdapter

        viewModel.fetchCountries().observe(
            this,
            Observer { countryList ->
                countryList.sortedBy { it.country}.forEach{countryListItem ->
                    if (countryListItem.country.isNotEmpty()){
                        countryAdapter.add(countryListItem.country)
                        countryNameSlugMap[countryListItem.country] = countryListItem.slug
                    }
                }
            }
        )
    }

    private fun informationAdapterInit(){
        // populado pela enum class Information
        val informationList = arrayListOf<String>()
        Information.values().forEach { informationList.add(it.type) }

        spinnerInfo.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, informationList)
        spinnerInfo.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(p0: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when(position){
                    Information.DAY_ONE.ordinal -> {
                        tvModoVisualizacao.visibility = View.VISIBLE // modo texto
                        rgModoVisualizacao.visibility = View.VISIBLE // modo grafico
                    }
                    Information.BY_COUNTRY.ordinal -> {
                        tvModoVisualizacao.visibility = View.GONE
                        rgModoVisualizacao.visibility = View.GONE
                    }
                }
            }

        }
    }

    private fun statusAdapterInit(){
        val statusList = arrayListOf<String>()
        Status.values().forEach { statusList.add(it.type) }

        spinnerStatus.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, statusList)
    }

    private fun fetchDayOne() {
        val countrySlug = countryNameSlugMap[spinnerPais.selectedItem.toString()]!!

        viewModel.fetchDayOne(countrySlug, spinnerStatus.selectedItem.toString()).observe(
            this,
            Observer { casesList ->
                if (rbTexto.isChecked) {
                    /* Modo texto */
                    modoGrafico(ligado = false)

                    tvResultado.text = if (casesListToString(casesList).isEmpty()) {
                        Toast.makeText(this, "Esse país não possui dados.", Toast.LENGTH_SHORT).show()
                        ""
                        } else {
                            casesListToString(casesList)
                        }

                } else {
                    /* Modo gráfico */
                    modoGrafico(ligado = true)

                    /* Preparando pontos */
                    val pointsArrayList = arrayListOf<DataPoint>()
                    casesList.forEach {
                        if (it.date != "0001-01-01T00:00:00Z"){
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.date.substring(0,10))
                            val point = DataPoint(date, it.cases.toDouble())
                            pointsArrayList.add(point)
                        }
                    }

                    if (pointsArrayList.isNotEmpty()) {
                        val pointsSeries = LineGraphSeries(pointsArrayList.toTypedArray())
                        gvResultado.addSeries(pointsSeries)

                        /* Formatando gráfico */
                        gvResultado.gridLabelRenderer.setHumanRounding(false)
                        gvResultado.gridLabelRenderer.labelFormatter =
                            DateAsXAxisLabelFormatter(this)

                        gvResultado.gridLabelRenderer.numHorizontalLabels = 4
                        val primeiraData = Date(pointsArrayList.first().x.toLong())
                        val ultimaData = Date(pointsArrayList.last().x.toLong())
                        gvResultado.viewport.setMinX(primeiraData.time.toDouble())
                        gvResultado.viewport.setMaxX(ultimaData.time.toDouble())
                        gvResultado.viewport.isXAxisBoundsManual = true

                        gvResultado.gridLabelRenderer.numVerticalLabels = 4
                        gvResultado.viewport.setMinY(pointsArrayList.first().y)
                        gvResultado.viewport.setMaxY(pointsArrayList.last().y)
                        gvResultado.viewport.isYAxisBoundsManual = true

                        val staticLabelsFormatter = DefaultLabelFormatter()

                    }else{
                        Toast.makeText(this, "Esse país não possui dados.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun fetchByCountry() {
        val countrySlug = countryNameSlugMap[spinnerPais.selectedItem.toString()]!!

        modoGrafico(ligado = false)
        viewModel.fetchByCountry(countrySlug, spinnerStatus.selectedItem.toString()).observe(
            this,
            Observer { casesList ->
                if (casesList.size > 0) {
                    tvResultado.text = casesListToString(casesList)
                } else {
                    gvResultado.removeAllSeries()
                    Toast.makeText(this, "Esse país não possui dados.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun modoGrafico(ligado: Boolean) {
        if (ligado) {
            tvResultado.visibility = View.GONE
            gvResultado.visibility = View.VISIBLE
        }
        else {
            tvResultado.visibility = View.VISIBLE
            gvResultado.visibility = View.GONE
        }
    }

    private inline fun <reified  T: ArrayList<*>> casesListToString(responseList: T): String {
        val resultSb = StringBuffer()

        // Usando class.java para não ter que adicionar biblioteca de reflexão Kotlin
        responseList.forEach {
            when(T::class.java) {
                DayOneResponseList::class.java -> {
                    with (it as DayOneResponseListItem) {
                        resultSb.append("Casos: ${this.cases}\n")
                        resultSb.append("Data: ${this.date.substring(0,10)}\n\n")
                    }
                }
                ByCountryResponseList::class.java -> {
                    with (it as ByCountryResponseListItem) {
                        this.province.takeIf { !this.province.isNullOrEmpty() }?.let { province ->
                            resultSb.append("Estado/Província: ${province}\n")
                        }
                        this.city.takeIf { !this.city.isNullOrEmpty() }?.let { city ->
                            resultSb.append("Cidade: ${city}\n")
                        }

                        resultSb.append("Casos: ${this.cases}\n")
                        resultSb.append("Data: ${this.date.substring(0,10)}\n\n")
                    }
                }
            }
        }

        return resultSb.toString()
    }


}
