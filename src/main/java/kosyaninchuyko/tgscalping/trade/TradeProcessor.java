package kosyaninchuyko.tgscalping.trade;

import kosyaninchuyko.tgscalping.ShareService;
import kosyaninchuyko.tgscalping.account.AccountService;
import kosyaninchuyko.tgscalping.order.Order;
import kosyaninchuyko.tgscalping.order.OrderService;
import kosyaninchuyko.tgscalping.trade.candle.HistoricCandleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.piapi.contract.v1.Candle;
import ru.tinkoff.piapi.contract.v1.MarketDataResponse;
import ru.tinkoff.piapi.contract.v1.OrderDirection;
import ru.tinkoff.piapi.contract.v1.OrderType;
import ru.tinkoff.piapi.core.stream.StreamProcessor;

import java.math.BigDecimal;

import static kosyaninchuyko.tgscalping.utils.ApiUtils.toBigDecimal;


public class TradeProcessor implements StreamProcessor<MarketDataResponse> {
    private static final Logger log = LoggerFactory.getLogger(TradeProcessor.class);

    private static final double MINIMAL_PERCENT = 1.025;
    private final HistoricCandleHandler historicCandleHandler;
    private final OrderService orderService;
    private final AccountService accountService;
    private final ShareService shareService;

    public TradeProcessor(HistoricCandleHandler historicCandleHandler,
                          OrderService orderService,
                          AccountService accountService,
                          ShareService shareService) {
        this.historicCandleHandler = historicCandleHandler;
        this.orderService = orderService;
        this.accountService = accountService;
        this.shareService = shareService;
    }

    @Override
    public void process(MarketDataResponse response) {
        //Делим текущую стоимость на цену открытия > MINIMAL_PERCENT
        AnalyticStatus realTimeCandleStatus = analyseRealTimeCandle(response);
        if (realTimeCandleStatus == AnalyticStatus.FAIL) {
            return;
        }
        //Получаем исторические свечки и реализуем алгоритм -> статус
        var historicCandleStatus = historicCandleHandler.handle();

        //Смотрим, чтобы оба вернули успех -> заявка
        if (historicCandleStatus == AnalyticStatus.SUCCESS) {
            createOrders(response);
        }
        log.info("\n    Share price = " + toBigDecimal(response.getCandle().getLow()));
    }

    private AnalyticStatus analyseRealTimeCandle(MarketDataResponse response) {
        Candle candle = response.getCandle();
//        System.out.println("\n      " + toBigDecimal(candle.getOpen()) + "\n      " +
//                toBigDecimal(candle.getClose()));
        if (toBigDecimal(candle.getOpen()).compareTo(toBigDecimal(candle.getClose())) < 0) {
            return AnalyticStatus.SUCCESS;
        } else {
            return AnalyticStatus.FAIL;
        }
    }

    private void createOrders(MarketDataResponse response) {
        orderService.createOrder(Order.builder()
                .withOrderType(OrderType.ORDER_TYPE_LIMIT)
                .withOrderDirection(OrderDirection.ORDER_DIRECTION_BUY)
                .withIntrumentId(shareService.getShareByTicker("YNDX").orElseThrow().getFigi())
                .withAccountId(accountService.getAccount().getId())
                .withQuantity(1L)
                .withPrice(toBigDecimal(response.getCandle().getLow()))
                .build()
        );
        orderService.createOrder(Order.builder()
                .withOrderType(OrderType.ORDER_TYPE_LIMIT)
                .withOrderDirection(OrderDirection.ORDER_DIRECTION_SELL)
                .withIntrumentId(shareService.getShareByTicker("YNDX").orElseThrow().getFigi())
                .withAccountId(accountService.getAccount().getId())
                .withQuantity(1L)
                .withPrice(toBigDecimal(response.getCandle().getLow()).add(BigDecimal.valueOf(3)))
                .build()
        );
    }
}
