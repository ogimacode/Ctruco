/*
 *  Copyright (C) 2023 Mateus Vieira and Stefhani Alkin - IFSP/SCL
 *  Contact: vieira <dot> mateus <at> aluno <dot> ifsp <dot> edu <dot> br
 *  Contact: s <dot> alkin <at> aluno <dot> ifsp <dot> edu <dot> br
 *
 *  This file is part of CTruco (Truco game for didactic purpose).
 *
 *  CTruco is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  CTruco is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CTruco.  If not, see <https://www.gnu.org/licenses/>
 */

package com.meima.skoltable;

import com.bueno.spi.model.CardRank;
import com.bueno.spi.model.CardToPlay;
import com.bueno.spi.model.GameIntel;
import com.bueno.spi.model.TrucoCard;
import com.bueno.spi.service.BotServiceProvider;

import java.util.Comparator;
import java.util.List;

public class SkolTable implements BotServiceProvider {
    @Override
    public boolean getMaoDeOnzeResponse(GameIntel intel) {
        List<TrucoCard> hand = intel.getCards();
        TrucoCard vira = intel.getVira();
        int opponentScore = intel.getOpponentScore();

        if (opponentScore == 11) {
            return true;
        }
        int handPowerRank = getPowerRankFirstRound(hand, vira);

        return handPowerRank >= 3;

    }

    @Override
    public boolean decideIfRaises(GameIntel intel) {
        final int elevenHandPoints = 11;
        final int maxHandPoints = 12;
        List<GameIntel.RoundResult> rounds = intel.getRoundResults();
        TrucoCard vira = intel.getVira();
        List<TrucoCard> hand = intel.getCards();

        if (intel.getScore() == elevenHandPoints ||
                intel.getOpponentScore() == elevenHandPoints ||
                intel.getHandPoints() == maxHandPoints) {
            return false;
        }

        if (rounds.isEmpty()) {
            int handPowerRank = getPowerRankFirstRound(hand, vira);

            return (handPowerRank == 4 || handPowerRank == 3);
        }

        if(rounds.get(0).equals(GameIntel.RoundResult.WON)){
            int handPowerRank = getPowerRankSecondRound(hand, vira);
            return (handPowerRank < 3);
        }

        if(rounds.get(0).equals(GameIntel.RoundResult.DREW)){
            int handPowerRank = getPowerRankSecondRound(hand, vira);
            return (handPowerRank > 3);
        }

        if(rounds.get(0).equals(GameIntel.RoundResult.LOST)){
            if(hasCopasAndZap(hand, vira)){
                return true;
            }

            int handPowerRank = getPowerRankSecondRound(hand, vira);
            return (handPowerRank >= 3);
        }

        return false;
    }

    @Override
    public CardToPlay chooseCard(GameIntel intel) {
        boolean isFirstRound = intel.getRoundResults().isEmpty();
        boolean existsOpponentCard = intel.getOpponentCard().isPresent();

        List<GameIntel.RoundResult> rounds = intel.getRoundResults();
        TrucoCard vira = intel.getVira();
        TrucoCard strongestCardInHand = getStrongestCardInHand(intel, vira);
        TrucoCard weakestCardInHand = getWeakestCardInHand(intel, vira);
        TrucoCard opponentCard;
        List<TrucoCard> hand = intel.getCards();


        if(isFirstRound){
            if(hasCopasAndZap(hand, vira)){
                return CardToPlay.of(weakestCardInHand);
            }

            if(existsOpponentCard) {
                opponentCard = intel.getOpponentCard().get();
                if(strongestCardInHand.compareValueTo(opponentCard, vira) > 0){
                    if(opponentCard.relativeValue(vira) < 8){
                        return CardToPlay.of(weakestCapableOfWin(opponentCard, vira, hand));
                    }
                    return CardToPlay.of(strongestCardInHand);
                } else {
                    return CardToPlay.of(weakestCardInHand);
                }
            }
            return CardToPlay.of(strongestCardInHand);
        }

        if (existsOpponentCard){
            opponentCard = intel.getOpponentCard().get();
            if (opponentCard.getRank().equals(CardRank.HIDDEN) || opponentCard.isZap(vira)){
                return CardToPlay.of(weakestCardInHand);
            }
        }

        if(rounds.get(0).equals(GameIntel.RoundResult.DREW) || rounds.get(0).equals(GameIntel.RoundResult.LOST)){
            return CardToPlay.of(strongestCardInHand);
        }

        if(rounds.get(0).equals(GameIntel.RoundResult.WON)){
            if(strongestCardInHand.isZap(vira) && rounds.size() == 1){
                return CardToPlay.of(weakestCardInHand);
            }
        }


        return CardToPlay.of(hand.get(0));
    }

    @Override
    public int getRaiseResponse(GameIntel intel) {
        boolean isFirstRound = intel.getRoundResults().isEmpty();
        List<GameIntel.RoundResult> rounds = intel.getRoundResults();
        List<TrucoCard> hand = intel.getCards();
        TrucoCard vira = intel.getVira();

        int handPowerRank = getPowerRankFirstRound(hand, vira);

        if (isPair(intel)) return 0;

        if(!isFirstRound){
            if(rounds.get(0).equals(GameIntel.RoundResult.WON)){
                handPowerRank = getPowerRankSecondRound(hand, vira);
                return switch (handPowerRank) {
                    case 4 -> 1;
                    case 3 -> 0;
                    default -> -1;
                };
            }
            return -1;
        }

        return switch (handPowerRank) {
            case 4 -> 1;
            case 3 -> 0;
            default -> -1;
        };
    }

    private TrucoCard getStrongestCardInHand(GameIntel intel, TrucoCard vira) {
        List<TrucoCard> cards = intel.getCards();

        return cards.stream()
                .max(Comparator.comparingInt(card -> card.relativeValue(vira))).get();
    }

    private TrucoCard getWeakestCardInHand(GameIntel intel, TrucoCard vira) {
        List<TrucoCard> cards = intel.getCards();

        return cards.stream().min(Comparator.comparingInt(card -> card.relativeValue(vira))).get();
    }

    private int getHandPower(List<TrucoCard> hand, TrucoCard vira){
        int power = 0;
        for (TrucoCard card: hand) {
            power += card.relativeValue(vira);
        }
        return power;
    }


    public int getPowerRankFirstRound(List<TrucoCard> hand, TrucoCard vira) {
        int power = getHandPower(hand, vira);

        if (power >= 28 && power <= 36) {
            return 4;
        } else if (power >= 20 && power <= 27) {
            return 3;
        } else if (power >= 13 && power <= 19) {
            return 2;
        } else {
            return 1;
        }
    }

    public int getPowerRankSecondRound(List<TrucoCard> hand, TrucoCard vira) {
        int power = getHandPower(hand, vira);

        if (power >= 21 && power <= 25) {
            return 4;
        } else if (power >= 16&& power <= 20) {
            return 3;
        } else if (power >= 11&& power <= 15) {
            return 2;
        } else {
            return 1;
        }
    }

    private boolean isPair(GameIntel intel) {
        long pairCount = intel.getCards().stream()
                .filter(card -> card.isManilha(intel.getVira()))
                .count();

        return pairCount == 2;
    }

    public boolean hasCopasAndZap(List<TrucoCard> hand ,TrucoCard vira) {
        boolean hasCopas = false;
        boolean hasZap = false;

        for (TrucoCard card : hand) {
            if (card.isCopas(vira)) {
                hasCopas = true;
            } else if (card.isZap(vira)) {
                hasZap = true;
            }

            if (hasCopas && hasZap) {
                break;
            }
        }

        return hasCopas && hasZap;
    }

    public TrucoCard weakestCapableOfWin(TrucoCard opponentCard, TrucoCard vira, List<TrucoCard> hand) {
        TrucoCard weakestCard = null;

        for (TrucoCard card : hand) {
            if (weakestCard == null || card.compareValueTo(opponentCard, vira) > 0) {
                if (weakestCard == null || card.compareValueTo(weakestCard, vira) < 0) {
                    weakestCard = card;
                }
            }
        }
        return weakestCard;
    }

}