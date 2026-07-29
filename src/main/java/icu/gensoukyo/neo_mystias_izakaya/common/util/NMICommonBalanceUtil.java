/*
 * Copyright 2026 NeoMystiasIzakaya Team
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package icu.gensoukyo.neo_mystias_izakaya.common.util;

import com.mojang.logging.LogUtils;
import icu.gensoukyo.neo_mystias_izakaya.api.event.server.economy.NMIBalanceEvent;
import icu.gensoukyo.neo_mystias_izakaya.common.network.ServerPayloadSender;
import icu.gensoukyo.neo_mystias_izakaya.content.economy.balance.NMIBalance;
import icu.gensoukyo.neo_mystias_izakaya.content.economy.balance.NMIBalanceEntry;
import icu.gensoukyo.neo_mystias_izakaya.content.economy.balance.NMIBalanceUnits;
import icu.gensoukyo.neo_mystias_izakaya.content.economy.transaction.NMIBalanceTransaction;
import icu.gensoukyo.neo_mystias_izakaya.content.economy.transaction.NMIBalanceTransactionEntry;
import icu.gensoukyo.neo_mystias_izakaya.content.economy.transaction.NMIBalanceTransactionReasons;
import icu.gensoukyo.neo_mystias_izakaya.registry.NMIAttachmentTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.slf4j.Logger;


public class NMICommonBalanceUtil {

    public static final Logger LOGGER = LogUtils.getLogger();

    public static NMIBalance get(Player player) {
        return player.getData(NMIAttachmentTypes.BALANCE).copy();
    }

    public static NMIBalance getWithOutCopy(Player player) {
        return player.getData(NMIAttachmentTypes.BALANCE);
    }

    public static void set(Player player, NMIBalance balance) {
        player.setData(NMIAttachmentTypes.BALANCE, balance);
    }

    public static long getEn(Player player) {
        return get(player, NMIBalanceUnits.EN);
    }

    public static void setEn(Player player, int en, boolean simulate) {
        set(player, NMIBalanceUnits.EN, en, simulate);
    }

    public static int insertEn(Player player, int en, boolean simulate) {
        return insert(player, NMIBalanceUnits.EN, en, simulate);
    }

    public static int extractEn(Player player, int en, boolean simulate) {
        return extract(player, NMIBalanceUnits.EN, en, simulate);
    }

    public static void setEn(Player player, int en, boolean simulate, Identifier reason, String from, String to) {
        set(player, NMIBalanceUnits.EN, en, simulate, reason, from, to);
    }

    public static int insertEn(Player player, int en, boolean simulate, Identifier reason, String from, String to) {
        return insert(player, NMIBalanceUnits.EN, en, simulate, reason, from, to);
    }

    public static int extractEn(Player player, int en, boolean simulate, Identifier reason, String from, String to) {
        return extract(player, NMIBalanceUnits.EN, en, simulate, reason, from, to);
    }

    public static long get(Player player, Identifier unit) {
        try (Transaction transaction = Transaction.openRoot()) {
            long count = 0;
            NMIBalance balance = getWithOutCopy(player);
            for (int i = 0; i < balance.size(); i++) {
                if (balance.getResource(i).is(unit)) {
                    count += balance.getAmountAsLong(i);
                }
            }
            transaction.commit();
            return count;
        } catch (Exception e) {
            LOGGER.trace("Failed to get {} balance for player {}: {}", unit, player.getName().getString(), e.getMessage());
            return 0;
        }
    }

    public static void set(Player player, Identifier unit, int count, boolean simulate) {
        if (count - (int) get(player, unit) > 0) {
            insert(player, unit, count - (int) get(player, unit), simulate);
        } else {
            extract(player, unit, count - (int) get(player, unit), simulate);
        }
    }

    public static void set(Player player, Identifier unit, int count, boolean simulate, Identifier reason, String from, String to) {
        if (count - (int) get(player, unit) > 0) {
            insert(player, unit, count - (int) get(player, unit), simulate, reason, from, to);
        } else {
            extract(player, unit, count - (int) get(player, unit), simulate, reason, from, to);
        }
    }

    public static int insert(Player player, Identifier unit, int count, boolean simulate) {
        return insert(player, unit, count, simulate, NMIBalanceTransactionReasons.UNDEFINED, "", player.getStringUUID());
    }

    public static int insert(Player player, Identifier unit, int count, boolean simulate, Identifier reason, String from, String to) {
        try (Transaction transaction = Transaction.openRoot()) {
            NMIBalance balance = getWithOutCopy(player);
            int canInsert = balance.insert(new NMIBalanceEntry(unit, count), count, transaction);
            NMIBalanceEvent.Insert post = NeoForge.EVENT_BUS.post(new NMIBalanceEvent.Insert(player, transaction, unit, count, simulate, reason, from, to));
            if (post.isCanceled()) {
                transaction.close();
                return 0;
            }
            if (simulate) {
                transaction.close();
                return canInsert;
            }
            transaction.commit();
            addTransactionEntry(player, new NMIBalanceTransactionEntry(post.getReason(), post.getUnit(), post.getCount(), post.getFrom(), post.getTo()));
            set(player, balance);
            return canInsert;
        } catch (Exception e) {
            LOGGER.trace("Failed to insert {} balance for player {}: {}", unit, player.getName().getString(), e.getMessage());
            return 0;
        }
    }

    public static int extract(Player player, Identifier unit, int count, boolean simulate) {
        return extract(player, unit, count, simulate, NMIBalanceTransactionReasons.UNDEFINED, player.getStringUUID(), "");
    }

    public static int extract(Player player, Identifier unit, int count, boolean simulate, Identifier reason, String from, String to) {
        try (Transaction transaction = Transaction.openRoot()) {
            NMIBalance balance = getWithOutCopy(player);
            int canExtract = balance.extract(new NMIBalanceEntry(unit, count), count, transaction);
            NMIBalanceEvent.Extract post = NeoForge.EVENT_BUS.post(new NMIBalanceEvent.Extract(player, transaction, unit, count, simulate, reason, from, to));
            if (post.isCanceled()) {
                transaction.close();
                return 0;
            }
            if (simulate) {
                transaction.close();
                return canExtract;
            }
            transaction.commit();
            addTransactionEntry(player, new NMIBalanceTransactionEntry(post.getReason(), post.getUnit(), post.getCount(), post.getFrom(), post.getTo()));
            set(player, balance);
            return canExtract;
        } catch (Exception e) {
            LOGGER.trace("Failed to extract {} balance for player {}: {}", unit, player.getName().getString(), e.getMessage());
            return 0;
        }
    }

    public static NMIBalanceTransaction getTransaction(Player player) {
        return player.getData(NMIAttachmentTypes.TRANSACTION);
    }

    public static void setTransaction(Player player, NMIBalanceTransaction transaction) {
        player.setData(NMIAttachmentTypes.TRANSACTION, transaction);
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPayloadSender.sendTransactionSyncMessage(serverPlayer, transaction);
        }
    }

    public static void addTransactionEntry(Player player, NMIBalanceTransactionEntry entry) {
        player.setData(NMIAttachmentTypes.TRANSACTION, getTransaction(player).addEntry(entry));
        if (player instanceof ServerPlayer serverPlayer) {
            ServerPayloadSender.sendTransactionUpdateMessage(serverPlayer, entry);
        }
    }
}
