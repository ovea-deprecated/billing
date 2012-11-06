require('Logger', 'options.ws.api', 'options.ws.resource');

if (jaxspot.subscription == undefined) {
    (function ($) {

        var logger = new Logger('jaxspot.subscription');

        jaxspot.subscription = {
            subscriptionUrl:function (provider, callback) {
                logger.debug('Subscribing to provider', provider);
                $.ajax({
                    type:'GET',
                    dataType:'json',
                    url:options.ws.api + '/billing/subscription/' + provider + '/service'
                }).success(function (response) {
                        callback.call(this, response.redirectURL);
                    }).error(function (xhr) {
                        jaxspot.bus.local.topic('/event/subscription/request/error').publish('subscription.message.' + $.parseJSON(xhr.responseText).message);
                    });
            },
            unsubscribe:function (provider) {
                logger.debug('Unsubscribe from provider', provider);
                $.ajax({
                    type:'GET',
                    dataType:'json',
                    url:options.ws.api + '/billing/subscription/' + provider + '/cancel'
                }).success(function (response) {
                        jaxspot.bus.local.topic('/event/unsubscription/success').publish(response.unSubscribeUrl);
                    }).error(function () {
                        jaxspot.bus.local.topic('/event/unsubscription/error').publish();
                    });
            }
        };
    })(jQuery);
}
