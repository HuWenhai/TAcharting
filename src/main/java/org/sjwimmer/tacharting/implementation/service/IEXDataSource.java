package org.sjwimmer.tacharting.implementation.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

import org.sjwimmer.tacharting.chart.api.OHLCVDataSource;
import org.sjwimmer.tacharting.chart.model.TaTimeSeries;
import org.sjwimmer.tacharting.chart.model.types.GeneralTimePeriod;
import org.sjwimmer.tacharting.chart.parameters.Parameter;
import org.sjwimmer.tacharting.implementation.model.api.key.IEXKey;
import org.sjwimmer.tacharting.implementation.util.FormatUtils;
import org.sjwimmer.tacharting.implementation.util.TimeSeriesConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.PrecisionNum;

import pl.zankowski.iextrading4j.api.stocks.Chart;
import pl.zankowski.iextrading4j.api.stocks.ChartRange;
import pl.zankowski.iextrading4j.client.IEXTradingClient;
import pl.zankowski.iextrading4j.client.rest.request.stocks.ChartRequestBuilder;

public class IEXDataSource implements OHLCVDataSource<IEXKey, Object>{

	private final static Logger log = LoggerFactory.getLogger(IEXDataSource.class);
	private final IEXDataSourceConfiguration config;
	final IEXTradingClient iexTradingClient = IEXTradingClient.create();
	
	public IEXDataSource(IEXDataSourceConfiguration configuration) {
		this.config = configuration;
	}
	
	public IEXDataSource() {
		this(new IEXDataSourceConfiguration());
	}
	
	@Override
	public List<String> getAllAvailableSymbols() throws Exception {
		return new ArrayList<String>();
	}

	@Override
	public TaTimeSeries getSymbolData(IEXKey symbol) throws Exception {
		return getSymbolData(symbol,ZonedDateTime.now().minusYears(100),ZonedDateTime.now().plusYears(100));
	}

	@Override
	public TaTimeSeries getSymbolData(IEXKey key, ZonedDateTime from, ZonedDateTime to) {
		IEXTimeSeriesConverter converter = new IEXTimeSeriesConverter(key.toString());
		List<Chart> data = new ArrayList<>();
		try {
			data = iexTradingClient.executeRequest(
					new ChartRequestBuilder()
						.withSymbol(key.toString())
						.withChartRange(config.getChartRange())
						.build());
		} catch(NullPointerException npe) {
			log.error(npe.getMessage());
		}	
		return converter.convert(data);
	}

	@Override
	public List<TaTimeSeries> getSymbolData(List<IEXKey> symbols, ZonedDateTime from, ZonedDateTime to){
		return symbols.stream().map(line -> {
			try {
				return getSymbolData(line);
			} catch (Exception e) {
				log.error(String.format("Error requesting data for %s (%s)",symbols.toString(), e.getMessage()));
				TimeSeries series = new BaseTimeSeries(line.toString(),Parameter.numFunction);
				return new TaTimeSeries(series, config.getCurrency() ,rangeToPeriod(config.getChartRange()));
			}
		}).collect(Collectors.toList());
	}

	//TODO check if this mapping is correct
	private GeneralTimePeriod rangeToPeriod(ChartRange chartRange) {
		switch(chartRange) {
			case DYNAMIC:{
				return GeneralTimePeriod.MINUTE;
			}
			case INTRADAY:{
				return GeneralTimePeriod.REALTIME;
			}
			default:{
				return GeneralTimePeriod.DAY;
			}
		}
	}

	@Override
	public boolean connect(Object c) {
		try {
			iexTradingClient.executeRequest(
					new ChartRequestBuilder()
					.withSymbol("AAPL")
					.withChartRange(ChartRange.ONE_MONTH)
					.build());
		} catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
			return false;
		}
		return true;
		
	}

	@Override
	public void disconnect() {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isReady() {
		return connect(Void.TYPE);
	}
	
	class IEXTimeSeriesConverter implements TimeSeriesConverter<List<Chart>>{

		private final String name;
		
		public IEXTimeSeriesConverter(String name){
			this.name = name;
		}
		
		@Override
		public TaTimeSeries convert(List<Chart> others) {
			TimeSeries series = new BaseTimeSeries(name, PrecisionNum::valueOf);
			for(Chart other: others) {
				ZonedDateTime time = ZonedDateTime.of(LocalDate.parse(other.getDate()),LocalTime.of(12, 0,0), ZoneId.systemDefault());
				series.addBar(
						new BaseBar(time, Parameter.numFunction.apply(other.getOpen()), Parameter.numFunction.apply(other.getHigh()), Parameter.numFunction.apply(other.getLow()), Parameter.numFunction.apply(other.getClose()), Parameter.numFunction.apply(other.getVolume()),Parameter.numFunction.apply(0)));
			}
			return new TaTimeSeries(series,Currency.getInstance("USD") ,FormatUtils.extractPeriod(series));
		}	
	}
}
