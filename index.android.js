'use strict'

import {
    NativeModules,
    DeviceEventEmitter
} from 'react-native';

const googleFit = NativeModules.RNGoogleFit;

class RNGoogleFit {
    constructor () {
    }

    async authorize () {
        try {
            return await googleFit.authorize();
        } catch (e) {
            console.error(e);
        }

        //return await googleFit.authorize();
    }

    async checkAuthorization () {
        let authResponse = await googleFit.checkAuthorization();

        if (authResponse && authResponse.authorized) {
            return true;
        } else {
            return false;
        }
    }

    //Will be deprecated in future releases
    getSteps (dayStart,dayEnd) {
        googleFit.getDailySteps(Date.parse(dayStart), Date.parse(dayEnd));
    }

    //Will be deprecated in future releases
    getWeeklySteps (startDate) {
        googleFit.getWeeklySteps(Date.parse(startDate), Date.now());
    }

    /**
     * Get the total steps per day over a specified date range.
     * @param {Object} options getDailyStepCountSamples accepts an options object containing required startDate: ISO8601Timestamp and endDate: ISO8601Timestamp.
     * @callback callback The function will be called with an array of elements.
     */

    getDailyStepCountSamples (options, callback) {
        let startDate = options.startDate != undefined ? Date.parse(options.startDate) : (new Date()).setHours(0,0,0,0);
        let endDate = options.endDate != undefined ? Date.parse(options.endDate) : (new Date()).valueOf();

        googleFit.getDailyStepCountSamples(startDate,
            endDate,
            (msg) => {
                callback(msg, false);
            },
            (res) => {
                if (res.length>0) {
                    callback(false, res.map(function(dev) {
                        var obj = {};
                        obj.source = dev.source.appPackage + ((dev.source.stream) ? ":" + dev.source.stream : "");
                        obj.steps = this.buildDailySteps(dev.steps);
                        return obj;
                        }, this)
                    );
                } else {
                    callback("There is no any steps data for this period", false);
                }
            });
    }

    getTotalStepsForRange (options, callback) {
        this.getDailyStepCountSamples(options, (msg, results) => {
            var total = 0;
            var googleFitResults = results[0] ? results[0].steps : [];

            for (let i = 0; i < googleFitResults.length; i++) {
                total += googleFitResults[i].value;
            }

            callback(msg, total);
        });
    }

    buildDailySteps (steps)
    {
        let results = {};
        for(var step of steps) {
            if (step == undefined) continue;

            var date = new Date(step.startDate);

            var day = ("0" + date.getDate()).slice(-2);
            var month = ("0" + (date.getMonth()+1)).slice(-2);
            var year = date.getFullYear();
            var dateFormatted = year + "-" + month + "-" + day;

            if (!(dateFormatted in results)) {
                results[dateFormatted] = 0;
            }

            results[dateFormatted] += step.steps;
        }

        let results2 = [];
        for(var index in results) {
            results2.push({date: index, value: results[index]});
        }
        return results2;
    }

    /**
     * Get the total distance per day over a specified date range.
     * @param {Object} options getDailyDistanceSamples accepts an options object containing required startDate: ISO8601Timestamp and endDate: ISO8601Timestamp.
     * @callback callback The function will be called with an array of elements.
     */

    getDailyDistanceSamples (options, callback) {
        let formattedDates = this.formatRangeDates(options),
            startDate = formattedDates.startDate,
            endDate = formattedDates.endDate;

        googleFit.getDailyDistanceSamples( startDate,
            endDate,
            (msg) => {
                callback(msg, false);
            },
            (res) => {
                if (res.length>0) {
                    res = res.map((el) => {
                        if (el.distance) {
                            el.startDate = new Date(el.startDate).toISOString();
                            el.endDate = new Date(el.endDate).toISOString();
                            return el;
                        }
                    });
                    callback(false, res.filter(day => day != undefined));
                } else {
                    callback("There is no any distance data for this period", false);
                }
            });
    }

    getDistanceByActivity (options, callback) {
        let formattedDates = this.formatRangeDates(options),
            startDate = formattedDates.startDate,
            endDate = formattedDates.endDate;

        googleFit.getDistanceByActivity(startDate,
            endDate,
            options.activity || "walking",
            (msg) => {
                callback(null);
            },
            (res) => {
                let distance = res.length && res[0] && res[0].distance ? res[0].distance : null;
                if (distance) {
                    callback(distance);
                } else {
                    callback(null);
                }
            });
    }

    /**
     * Query for weight samples. the options object is used to setup a query to retrieve relevant samples.
     * @param {Object} options  getDailyStepCountSamples accepts an options object containing unit: "pound"/"kg",
     *                          startDate: ISO8601Timestamp and endDate: ISO8601Timestamp.
     * @callback callback The function will be called with an array of elements.
     */

    getWeightSamples (options, callback) {
        let startDate = Date.parse(options.startDate);
        let endDate = Date.parse(options.endDate);
        googleFit.getWeightSamples( startDate,
            endDate,
            (msg) => {
                callback(msg, false);
            },
            (res) => {
                if (res.length>0) {
                    res = res.map((el) => {
                        if (el.value) {
                            if (options.unit == 'pound') {
                                el.value = this.KgToLbs(el.value); //convert back to pounds
                            }
                            el.startDate = new Date(el.startDate).toISOString();
                            el.endDate = new Date(el.endDate).toISOString();
                            return el;
                        }
                    });
                    callback(false, res.filter(day => day != undefined));
                } else {
                    callback("There is no any weight data for this period", false);
                }
            });
    }

    saveWeight (options, callback) {
        if (options.unit == 'pound') {
            options.value = this.lbsAndOzToK({ pounds: options.value, ounces: 0 }); //convert pounds and ounces to kg
        }
        options.date = Date.parse(options.date);
        googleFit.saveWeight( options,
            (msg) => {
                callback(msg,false);
            },
            (res) => {
                callback(false,res);

            });
    }

    deleteWeight (options, callback) {
        if (options.unit == 'pound') {
            options.value = this.lbsAndOzToK({ pounds: options.value, ounces: 0 }); //convert pounds and ounces to kg
        }
        options.date = Date.parse(options.date);
        googleFit.deleteWeight( options,
            (msg) => {
                callback(msg,false);
            },
            (res) => {
                callback(false,res);

            });
    }

    async isAvailable() { // true if GoogleFit installed
        return await googleFit.isAvailable();
    }

    isEnabled (callback) { // true if permission granted
        googleFit.isEnabled(
            (msg) => {
                callback(msg,false);
            },
            (res) => {
                callback(false,res);
            });
    }

    observeSteps (callback) {
        DeviceEventEmitter.addListener(
            'StepChangedEvent',
            (steps) => callback(steps)
        );

        googleFit.observeSteps();
    }

    observeHistory (callback) {
        DeviceEventEmitter.addListener(
            'StepHistoryChangedEvent',
            (steps) => callback(steps)
        );
    }

    onAuthorize (callback) {
        DeviceEventEmitter.addListener(
            'AuthorizeEvent',
            (authorized) => callback(authorized)
        );
    }

    usubscribeListeners () {
        DeviceEventEmitter.removeAllListeners();
    }

    lbsAndOzToK (imperial) {
        let pounds = imperial.pounds + imperial.ounces / 16;
        return pounds * 0.45359237;
    }

    KgToLbs (metric) {
        return metric * 2.2046;
    }

    formatRangeDates (dateData) {
        return {
            startDate: dateData.startDate != undefined ? Date.parse(dateData.startDate) : (new Date()).setHours(0,0,0,0),
            endDate: dateData.endDate != undefined ? Date.parse(dateData.endDate) : (new Date()).getTime()
        };
    }
}

export default new RNGoogleFit();
