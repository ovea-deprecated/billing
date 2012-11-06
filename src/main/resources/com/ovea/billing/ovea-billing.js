;
(function () {

    window.Billing = function (opts) {

        this.config = $.extend({
            url:'',
            platforms: ['mpulse', 'facebook', 'paypal'],
            redirect:function (url) {
                //TODO: check if more params are required
                window.location = url;
            }
        }, opts || {});

        if (!this.config.url) {
            throw new Error('Missing billing service URL');
        }

        return {
            buy:function (pack) {

            },
            cancel:function (pack) {

            }
        }
    };

})();
