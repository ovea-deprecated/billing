;
(function ($) {

    window.Billing = function (opts) {
        var def_handler, self, trigger, ready = false, conf = {
            products:[],
            platforms:[]
        };
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
                    alert('[billing] ' + e.type + ': ' + e.message);
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
                onevent:$.noop
            }, opts || {}),
            toString:function () {
                return 'Billing{url=' + this.options.url + ', products=' + conf.products + ', platforms=' + conf.platforms + '}';
            },
            init:function () {
                $.ajax({
                    url:self.options.url + '/options',
                    type:'POST',
                    dataType:'json',
                    data:{
                        action:'options'
                    },
                    success:function (options) {
                        conf = $.extend(conf, options || {});
                        ready = true;
                        trigger('ready');
                    }
                });
            },
            buy:function (opts) {
                if (!ready) throw new Error("Not ready yet. Please call init() method first.");
                if (!opts.product) throw new Error("Missing 'product' option to select product to buy");
                if (!opts.using) throw new Error("Missing 'using' option to select billing platform");
                if ($.inArray(opts.product, conf.products) === -1) throw new Error("Bad product: " + opts.product + ". Supported products: " + conf.products);
                if ($.inArray(opts.using, conf.platforms) === -1) throw new Error("Bad platform: " + opts.using + ". Supported platforms: " + conf.platforms);
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
                            trigger('buy.redirect', {
                                url:data.redirect,
                                action:'buy',
                                product:opts.product,
                                platform:opts.using
                            });
                        } else {
                            trigger('buy.success', {
                                product:opts.product,
                                platform:opts.using
                            });
                        }
                    },
                    error:function (xhr) {
                        trigger('buy.error', {
                            code:xhr.status,
                            message:$.parseJSON(xhr.responseText).message,
                            product:opts.product,
                            platform:opts.using
                        });
                    }
                });
            },
            cancel:function (opts) {
                if (!ready) throw new Error("Not ready yet. Please call init() method first.");
                if (!opts.product) throw new Error("Missing 'product' option to select product to cancel");
                $.ajax({
                    url:self.options.url + '/cancel/' + opts.product,
                    type:'POST',
                    dataType:'json',
                    data:{
                        action:'cancel',
                        product:opts.product
                    },
                    success:function (data) {
                        if (data.error) {
                            trigger('cancel.error', {
                                message:data.error,
                                product:opts.product
                            });
                        } else if (data.redirect) {
                            trigger('cancel.redirect', {
                                url:data.redirect,
                                action:'cancel',
                                product:opts.product
                            });
                        } else {
                            trigger('cancel.success', {
                                product:opts.product
                            });
                        }
                    },
                    error:function (xhr) {
                        trigger('cancel.error', {
                            code:xhr.status,
                            message:$.parseJSON(xhr.responseText).message,
                            product:opts.product
                        });
                    }
                });
            },
            isReady:function () {
                return ready;
            }
        };
        if (!self.options.url) throw new Error('Missing billing service URL');
        return self;
    };

})(jQuery);
