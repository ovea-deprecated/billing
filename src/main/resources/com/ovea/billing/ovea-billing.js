;
(function ($) {

    window.Billing = function (opts) {
        var def_handler, self, trigger, ready = false;
        def_handler = function (e) {
            switch (e.type) {
                case 'ready':
                    break;
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
        trigger = function (name, data) {
            var e = $.Event(name);
            $.extend(e, data || {});
            if ($.isFunction(self.options.onevent)) {
                var ret = self.options.onevent.call(self, e);
                if (ret !== false && !e.isDefaultPrevented()) {
                    def_handler.call(self, e);
                }
            }
        };
        self = {
            options:$.extend({
                url:'',
                onevent:$.noop,
                products:[],
                platforms:[]
            }, opts || {}),
            toString:function () {
                return 'Billing@' + this.options.url;
            },
            buy:function (opts) {
                if (!ready) throw new Error("Not ready yet");
                if (!opts.product) throw new Error("Missing 'product' option to select product to buy");
                if (!opts.using) throw new Error("Missing 'using' option to select billing platform");
                if ($.inArray(opts.product, self.options.products) === -1) throw new Error("Bad product: " + opts.product + ". Supported products: " + self.options.products);
                if ($.inArray(opts.using, self.options.platforms) === -1) throw new Error("Bad platform: " + opts.using + ". Supported platforms: " + self.options.platforms);
                $.ajax({
                    url:self.options.url + '/buy/' + opts.product + '/using/' + opts.using,
                    type:'POST',
                    dataType:'json',
                    data:{
                        action:'buy',
                        product:opts.product,
                        platform:opts.using
                    },
                    success:function (data) {
                        if (data.error) {
                            trigger('buy.error', {
                                message:data.error,
                                product:opts.product,
                                platform:opts.using
                            });
                        } else if (data.redirect) {
                            trigger('redirect', {
                                url:data.redirect,
                                product:opts.product,
                                platform:opts.using
                            });
                        }
                    },
                    error:function (xhr) {
                        trigger('buy.error', {
                            message:'http-status-' + xhr.status,
                            product:opts.product,
                            platform:opts.using
                        });
                    }
                });
            },
            cancel:function (opts) {
                if (!ready) throw new Error("Not ready yet");
                if (!opts.product) throw new Error("Missing 'product' option to select product to buy");
                // mpulse: call backend, send redirect event if we get an url (or call canceled event), redirect if not return false or send error messsage
                // url:options.ws.api + '/billing/subscription/' + provider + '/cancel'
            },
            isReady:function () {
                return ready;
            }
        };
        if (!self.options.url) throw new Error('Missing billing service URL');
        $.ajax({
            url:self.options.url + '/options',
            type:'GET',
            dataType:'json',
            success:function (options) {
                $.extend(self.options, options);
                ready = true;
                trigger('ready');
            }
        });
        return self;
    };

})(jQuery);
