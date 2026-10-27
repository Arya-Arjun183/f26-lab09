package edu.cmu.cs214.campusreserve.billing;

import static edu.cmu.cs214.campusreserve.billing.support.BillingFixtures.auditorium;
import static edu.cmu.cs214.campusreserve.billing.support.BillingFixtures.context;
import static edu.cmu.cs214.campusreserve.billing.support.BillingFixtures.interval;
import static edu.cmu.cs214.campusreserve.billing.support.BillingFixtures.studyRoom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.billing.rules.BaseChargeRule;
import edu.cmu.cs214.campusreserve.billing.rules.LargeGroupSurchargeRule;
import edu.cmu.cs214.campusreserve.billing.rules.LongBookingDiscountRule;
import edu.cmu.cs214.campusreserve.billing.rules.MinimumChargeRule;
import edu.cmu.cs214.campusreserve.billing.rules.PeakHourSurchargeRule;
import edu.cmu.cs214.campusreserve.core.domain.RoomKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PricingTest {

    private static final Money NOTHING = Money.zero("USD");

    private final RateCard card = RateCard.standard();
    private final PriceCalculator calculator = PriceCalculator.standard();

    @Nested
    class Amounts {

        @Test
        void formatsAmountsWithTheirSign() {
            assertEquals("USD 12.50", Money.usd(1_250).format());
            assertEquals("USD 0.05", Money.usd(5).format());
            assertEquals("USD 0.00", Money.zero("USD").format());
            assertEquals("EUR 3.00", Money.of("EUR", 300).format());
            assertEquals("USD -12.50", Money.usd(-1_250).format());
            assertEquals("USD -0.05", Money.usd(-5).format());
        }

        @Test
        void addsSubtractsMultipliesComparesAndReportsItsSign() {
            Money five = Money.usd(500);
            assertEquals(Money.usd(750), five.plus(Money.usd(250)));
            assertEquals(Money.usd(250), five.minus(Money.usd(250)));
            assertEquals(Money.usd(1_500), five.times(3));
            assertEquals(Money.usd(-500), five.negated());
            assertTrue(NOTHING.isZero());
            assertTrue(five.isPositive());
            assertTrue(five.negated().isNegative());
            assertTrue(Money.usd(100).isLessThan(five));
            assertTrue(five.isGreaterThan(Money.usd(100)));
            assertFalse(five.isLessThan(Money.usd(500)));
            assertEquals(0, five.compareTo(Money.usd(500)));
        }

        @Test
        void roundsHalfAwayFromZeroWhateverTheFraction() {
            assertEquals(Money.usd(51), Money.usd(101).percentage(50));
            assertEquals(Money.usd(-51), Money.usd(-101).percentage(50));
            assertEquals(Money.usd(225), Money.usd(1_500).percentage(15));
            assertEquals(Money.usd(0), Money.usd(1_500).percentage(0));
            assertEquals(Money.usd(750), Money.usd(500).prorate(90, 60));
            assertEquals(Money.usd(167), Money.usd(500).prorate(20, 60));
            assertEquals(Money.usd(500), Money.usd(500).prorate(60, 60));
        }

        @Test
        void refusesMixedCurrenciesMalformedCodesAndBadFractions() {
            Money dollars = Money.usd(500);
            Money euros = Money.of("EUR", 500);
            assertThrows(IllegalArgumentException.class, () -> dollars.plus(euros));
            assertThrows(IllegalArgumentException.class, () -> dollars.minus(euros));
            assertThrows(IllegalArgumentException.class, () -> dollars.compareTo(euros));
            assertThrows(IllegalArgumentException.class, () -> Money.of("usd", 100));
            assertThrows(IllegalArgumentException.class, () -> Money.of("US", 100));
            assertThrows(IllegalArgumentException.class, () -> Money.of("", 100));
            assertThrows(IllegalArgumentException.class, () -> dollars.percentage(-1));
            assertThrows(IllegalArgumentException.class, () -> dollars.prorate(10, 0));
        }
    }

    @Nested
    class Cards {

        @Test
        void publishesARateForEveryKindAndAHalfOpenPeakWindow() {
            for (RoomKind kind : RoomKind.values()) {
                assertTrue(card.hourlyRate(kind).isPositive(), "No rate for " + kind);
            }
            assertEquals(Money.usd(500), card.hourlyRate(RoomKind.STUDY_ROOM));
            assertEquals(Money.usd(6_000), card.hourlyRate(RoomKind.AUDITORIUM));
            assertEquals("USD", card.currency());
            assertFalse(card.isPeakStart(13 * 60 - 1));
            assertTrue(card.isPeakStart(13 * 60));
            assertTrue(card.isPeakStart(17 * 60 - 1));
            assertFalse(card.isPeakStart(17 * 60));
        }

        @Test
        void copyWithMethodsLeaveTheOriginalAlone() {
            RateCard cheaper = card.withHourlyRate(RoomKind.STUDY_ROOM, Money.usd(100))
                .withPeakSurchargePercent(0)
                .withLargeGroup(4, 50)
                .withLongBooking(30, 5)
                .withMinimumCharge(Money.usd(0))
                .withPeakWindow(60, 120);

            assertEquals(Money.usd(100), cheaper.hourlyRate(RoomKind.STUDY_ROOM));
            assertEquals(0, cheaper.peakSurchargePercent());
            assertEquals(4, cheaper.largeGroupThreshold());
            assertEquals(30, cheaper.longBookingMinutes());
            assertEquals(Money.usd(0), cheaper.minimumCharge());
            assertTrue(cheaper.isPeakStart(90));

            assertEquals(Money.usd(500), card.hourlyRate(RoomKind.STUDY_ROOM));
            assertEquals(20, card.peakSurchargePercent());
            assertEquals(Money.usd(300), card.minimumCharge());
        }

        @Test
        void refusesACardThatCannotBeBilledFrom() {
            Map<RoomKind, Money> onlyOneKind = new EnumMap<>(RoomKind.class);
            onlyOneKind.put(RoomKind.STUDY_ROOM, Money.usd(500));
            assertThrows(IllegalArgumentException.class,
                () -> new RateCard(onlyOneKind, 780, 1_020, 20, 25, 10, 180, 15, Money.usd(300)));
            assertThrows(IllegalArgumentException.class, () -> card.withPeakWindow(600, 600));
            assertThrows(IllegalArgumentException.class, () -> card.withPeakWindow(600, 2_000));
            assertThrows(IllegalArgumentException.class, () -> card.withPeakSurchargePercent(-5));
            assertThrows(IllegalArgumentException.class, () -> card.withLongBooking(180, 150));
            assertThrows(IllegalArgumentException.class, () -> card.withLargeGroup(0, 10));
            assertThrows(IllegalArgumentException.class, () -> card.withMinimumCharge(Money.usd(-1)));
            assertThrows(IllegalArgumentException.class, () -> card.withMinimumCharge(Money.of("EUR", 300)));
        }
    }

    @Nested
    class Rules {

        @Test
        void baseChargeBillsPartialHoursByTheMinute() {
            List<LineItem> lines = new BaseChargeRule()
                .apply(context(studyRoom("r1"), interval("09:00", "10:30"), 4), NOTHING);

            assertEquals(1, lines.size());
            assertEquals(BaseChargeRule.CODE, lines.get(0).code());
            assertEquals(Money.usd(750), lines.get(0).amount());
            assertEquals(Money.usd(167), new BaseChargeRule()
                .apply(context(studyRoom("r1"), interval("09:00", "09:20"), 4), NOTHING).get(0).amount());
        }

        @Test
        void surchargesAndTheDiscountFireOnlyOnTheBookingsTheyName() {
            PeakHourSurchargeRule peak = new PeakHourSurchargeRule();
            LargeGroupSurchargeRule group = new LargeGroupSurchargeRule();
            LongBookingDiscountRule longBooking = new LongBookingDiscountRule();

            assertTrue(peak.apply(context(studyRoom("r1"), interval("09:00", "10:00"), 4), NOTHING).isEmpty());
            assertTrue(peak.apply(context(studyRoom("r1"), interval("12:00", "14:00"), 4), NOTHING).isEmpty());
            assertTrue(peak.apply(context(studyRoom("r1"), interval("14:00", "15:00"), 4,
                card.withPeakSurchargePercent(0)), NOTHING).isEmpty());
            assertEquals(Money.usd(100),
                peak.apply(context(studyRoom("r1"), interval("14:00", "15:00"), 4), NOTHING).get(0).amount());

            assertTrue(group.apply(context(auditorium("h1"), interval("09:00", "10:00"), 24), NOTHING).isEmpty());
            LineItem atThreshold =
                group.apply(context(auditorium("h1"), interval("09:00", "10:00"), 25), NOTHING).get(0);
            assertEquals(LargeGroupSurchargeRule.CODE, atThreshold.code());
            assertEquals(Money.usd(600), atThreshold.amount());

            PriceContext justUnder = context(studyRoom("r1"), interval("09:00", "11:59"), 4);
            assertTrue(longBooking.apply(justUnder, NOTHING).isEmpty());
            LineItem credit =
                longBooking.apply(context(studyRoom("r1"), interval("09:00", "12:00"), 4), NOTHING).get(0);
            assertEquals(Money.usd(-225), credit.amount());
            assertTrue(credit.isCredit());
        }
    }

    @Nested
    class Pipeline {

        @Test
        void quietBookingPaysForItsTimeAndAShortOneMeetsTheFloor() {
            PriceQuote quote = calculator.quote(studyRoom("r1"), interval("09:00", "10:30"), 4, card);
            assertEquals(List.of(BaseChargeRule.CODE), quote.codes());
            assertEquals(Money.usd(750), quote.total());
            assertEquals(1, quote.size());
            assertEquals("USD", quote.currency());
            String text = quote.format();
            assertTrue(text.contains("90 min in Study r1 (Study room) at USD 5.00 per hour"), text);
            assertTrue(text.contains("TOTAL  USD 7.50"), text);

            PriceQuote shortStay = calculator.quote(studyRoom("r1"), interval("09:00", "09:20"), 2, card);
            assertEquals(Money.usd(167), shortStay.lineItem("BASE").orElseThrow().amount());
            assertEquals(Money.usd(133), shortStay.lineItem("MIN").orElseThrow().amount());
            assertEquals(Money.usd(300), shortStay.total());

            PriceQuote exactlyAtTheFloor = calculator.quote(studyRoom("r1"), interval("09:00", "09:36"), 2, card);
            assertEquals(List.of(BaseChargeRule.CODE), exactlyAtTheFloor.codes());
            assertEquals(Money.usd(300), exactlyAtTheFloor.total());
        }

        @Test
        void surchargesScaleOffRoomTimeRatherThanOffEachOther() {
            assertEquals(List.of("BASE", "PEAK", "GROUP", "LONG", "MIN"), calculator.ruleCodes());

            PriceQuote hour = calculator.quote(auditorium("h1"), interval("14:00", "15:00"), 30, card);
            assertEquals(Money.usd(6_000), hour.lineItem("BASE").orElseThrow().amount());
            assertEquals(Money.usd(1_200), hour.lineItem("PEAK").orElseThrow().amount());
            assertEquals(Money.usd(600), hour.lineItem("GROUP").orElseThrow().amount());
            assertEquals(Money.usd(7_800), hour.total());

            PriceQuote afternoon = calculator.quote(auditorium("h1"), interval("14:00", "17:00"), 30, card);
            assertEquals(List.of("BASE", "PEAK", "GROUP", "LONG"), afternoon.codes());
            assertEquals(Money.usd(18_000), afternoon.lineItem("BASE").orElseThrow().amount());
            assertEquals(Money.usd(3_600), afternoon.lineItem("PEAK").orElseThrow().amount());
            assertEquals(Money.usd(1_800), afternoon.lineItem("GROUP").orElseThrow().amount());
            assertEquals(Money.usd(-2_700), afternoon.lineItem("LONG").orElseThrow().amount());
            assertEquals(Money.usd(20_700), afternoon.total());
        }

        @Test
        void theFloorHasToRunAfterTheDiscount() {
            RateCard steepDiscount = card.withLongBooking(60, 100);
            PriceContext ctx = PriceContext.of(studyRoom("r1"), interval("09:00", "10:00"), 4, steepDiscount);

            PriceQuote inOrder = new PriceCalculator(
                List.of(new BaseChargeRule(), new LongBookingDiscountRule(), new MinimumChargeRule())).quote(ctx);
            PriceQuote floorTooEarly = new PriceCalculator(
                List.of(new BaseChargeRule(), new MinimumChargeRule(), new LongBookingDiscountRule())).quote(ctx);

            assertEquals(Money.usd(300), inOrder.total());
            assertTrue(inOrder.hasLineItem("MIN"));
            assertEquals(Money.usd(0), floorTooEarly.total());
            assertFalse(floorTooEarly.hasLineItem("MIN"));
        }

        @Test
        void refusesAPipelineOrAQuoteItCannotBuild() {
            assertThrows(IllegalArgumentException.class, () -> new PriceCalculator(List.of()));
            assertThrows(IllegalArgumentException.class,
                () -> new PriceCalculator(List.of(new BaseChargeRule(), new BaseChargeRule())));
            List<LineItem> lines = List.of(LineItem.of("BASE", "Room time", Money.usd(750)));
            assertThrows(IllegalArgumentException.class, () -> new PriceQuote(lines, Money.usd(700)));
            assertThrows(IllegalArgumentException.class, () -> new PriceQuote(List.of(), Money.usd(0)));
        }
    }
}
