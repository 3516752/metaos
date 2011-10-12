/*
 * Copyright 2011 - 2012
 * All rights reserved. License and terms according to LICENSE.txt file.
 * The LICENSE.txt file and this header must be included or referenced 
 * in each piece of code derived from this project.
 */
package com.metaos.ext;

import java.util.*;
import com.metaos.datamgt.*;
import com.metaos.util.*;

/**
 * Transposes data, resulting into a set of prices organized firstly by
 * instant and secondly by day.
 * Internally, its implemented as a dense matrix - ie it wouldn't be efficient
 * for parse matrices as in tick-data
 * @deprecated.
 */
public class VolumeViews implements Listener {
    private final CalUtils.InstantGenerator instantGenerator;
    // valuesInstantDay is a dense matrix
    private final List<List<Double>> valuesInstantDay;  // First index:instant
    private final List<Calendar> consideredDays;

    private Calendar lastDay = null;
    private int processedDays = 0;
    private static final Field _volume_ = new Field.VOLUME();


    /**
     * Creates a transposer subscribed to given lines accumulator.
     */
    public VolumeViews(final SpreadTradesMgr accumulator, 
            final CalUtils.InstantGenerator instantGenerator) {
        accumulator.addListener(this);
        this.instantGenerator = instantGenerator;
        this.valuesInstantDay = new ArrayList<List<Double>>();
        this.consideredDays = new ArrayList<Calendar>();
    }


    /**
     * Receives notification signals.
     */
    public void notify(final ParseResult parseResult) {
        if(parseResult.values(0).get(_volume_)==null) return;
        final Calendar currentDay = CalUtils.normalizedClone(
                parseResult.getTimestamp());
        if(this.lastDay!=null && this.lastDay.before(currentDay)) {
            consolidateDay(currentDay);
        } else if(this.lastDay==null) {
            this.consideredDays.add(currentDay);
            this.lastDay = currentDay;
        }

        final int instant = this.instantGenerator.generate(
                parseResult.getTimestamp());
        
        while(this.valuesInstantDay.size()<=instant) {
            final List<Double> l = new ArrayList<Double>();
            for(int i=0; i<this.processedDays; i++) {
                l.add(null);
            }
            this.valuesInstantDay.add(l);
        }

        this.valuesInstantDay.get(instant).add(
                parseResult.values(0).get(_volume_));
    }


    //
    // Extra methods ---------------------------
    //


    /**
     * Ballances passed day and prepares object to process next day;
     * Should be called when no more data will be notified.
     * @param day ended day to ballance (may be null if no more days are going
     * to be processed).
     */
    public void consolidateDay(final Calendar day) {
        this.lastDay = day;
        this.processedDays = this.processedDays + 1;    
        this.consideredDays.add(day);

        // Ballances all instants 
        for(final List<Double> l : this.valuesInstantDay) {
            while(l.size()<this.processedDays) l.add(null);
        }
    }


    /**
     * Returns true if there is no element.
     */
    public boolean isEmpty() {
        return this.valuesInstantDay.size()==0;
    }
    

    /**
     * Normalizes data such that all values per day have the same accumulated
     * value, thus sum(x(i,day), i=0...) = K for every day.
     */
    public void normalizeDays(final double k) {
        for(int i=0; i<this.valuesInstantDay.get(0).size(); i++) {
            double total = 0;
            for(int j=0; j<this.valuesInstantDay.size(); j++) {
                if(this.valuesInstantDay.get(j)==null) continue;
                if(this.valuesInstantDay.get(j).get(i)==null) continue;
                total += this.valuesInstantDay.get(j).get(i);
            }
            for(int j=0; j<this.valuesInstantDay.size(); j++) {
                if(this.valuesInstantDay.get(j)==null) continue;
                if(this.valuesInstantDay.get(j).get(i)==null) continue;
                this.valuesInstantDay.get(j).set(i,
                            k * this.valuesInstantDay.get(j).get(i) / total);
            }
        }
    }


    /**
     * Gets the list of values for every day for a fixed moment in day.
     */
    public List<Double> getValueAcrossDays(final int instant) {
        return this.valuesInstantDay.get(instant);
    }


    /**
     * Gets the list of values for one day.
     */
    public List<Double> getValuesWithinDay(final Calendar day) {
        final Calendar when = CalUtils.normalizedClone(day);
        for(int i=0; i<this.consideredDays.size(); i++) {
            final Calendar c = this.consideredDays.get(i);
            if(c!=null && c.equals(when)) {
                final List<Double> result = new ArrayList<Double>();
                for(int m=0; m<this.valuesInstantDay.size(); m++) {
                    result.add(this.valuesInstantDay.get(m).get(i));
                }
                return result;
            }
        }
        throw new NoSuchElementException("No such data for day " + when);
    }


    /**
     * Gets the number of instants for each considered day.
     */
    public int numberOfInstantsInADay() {
        return this.valuesInstantDay.size();
    }


    /**
     * Gets the list of considered days.
     */
    public List<Calendar> getConsideredDays() {
        return this.consideredDays;
    }
}