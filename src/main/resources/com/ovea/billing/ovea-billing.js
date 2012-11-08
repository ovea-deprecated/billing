;
(function ($) {

    window.Billing = function (opts) {
        var cb, self;
        cb = function (e) {
            switch (e.type) {
                case 'buy.redirect':
                case 'cancel.redirect':
                    window.location = e.url;
                    break;
                case 'buy.error':
                case 'cancel.error':
                    alert('Billing error: ' + e.message);
                    break;
                case 'buy.success':
                case 'cancel.success':
                    break;
            }
        };
        self = {
            options:$.extend({
                url:'',
                platforms:['mpulse', 'facebook', 'paypal'],
                onevent:$.noop
            }, opts || {}),
            toString:function () {
                return 'Billing@' + this.options.url;
            },
            buy:function (opts) {
                // mpulse: get url, send redirect event, redirect if not return false or send error messsage
                // url:options.ws.api + '/billing/subscription/' + provider + '/service'
            },
            cancel:function (opts) {
                // mpulse: call backend, send redirect event if we get an url (or call canceled event), redirect if not return false or send error messsage
                // url:options.ws.api + '/billing/subscription/' + provider + '/cancel'
            }
        };
        if (!self.options.url) throw new Error('Missing billing service URL');
        return self;
    };

})(jQuery);
