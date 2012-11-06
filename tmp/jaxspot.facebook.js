require('jaxspot', 'jaxspot.bus.local', 'jaxspot.navigation', 'jaxspot.bus.local.sync');

if (jaxspot.facebook == undefined) {
    jaxspot.facebook = {};

    (function ($) {

        var logger = new Logger('jaxspot.facebook');

        jaxspot.facebook = {
            login:function () {
                logger.debug('Facebook login');
                FB.login(
                    function (response) {
                        jaxspot.facebook.loginSucceed(response.authResponse.accessToken, response.authResponse.expiresIn);
                    }, {scope:'email,publish_stream'}
                );
            },
            loginSucceed:function (accessToken, expires) {
                logger.debug('Facebook login callback success');
                $.get(options.ws.api + '/security/facebook/login?accessToken=' + accessToken + '&expiresIn=' + expires, function () {
                    jaxspot.navigation.redirectAndPublish(options.ws.app, '/event/security/member/logged');
                });
            },
            invite:function () {
                FB.ui({ method:'apprequests', message:jaxspot.i18n.message('facebook.invitation.message')});
            },
            buy:function (_package) {
                var payload = {
                    method:'pay',
                    order_info: _package,
                    action:'buy_item',
                    dev_purchase_params:{'oscif':false}
                };

                FB.ui(payload, function (data) {
                    if (data['order_id']) {
                        jaxspot.security.refreshAuthz(function () {
                            jaxspot.bus.local.topic('/event/subscription/success').publish();
                        });
                    } else {
                        jaxspot.bus.local.topic('/event/subscription/failure').publish();
                    }
                });
            },
            subscribe:function(_package) {
                var payload = {
                    method: 'pay',
                    action: 'create_subscription',
                    product: options.ws.resource + '/og/' +  _package + '.html'
                };
                FB.ui(payload, function(data) {
                    alert('Subscription Completed');
                    console.log(data);
                });
            },
            friends:function () {
                FB.getLoginStatus(function (response) {
                    if (response.status === 'connected') {
                        FB.api('/me/friends', function (response) {
                                var fb_friends = [];
                                if (response.data) {
                                    for (var i = 0; i < response.data.length; i++) {
                                        fb_friends.push({fb_id:response.data[i].id, name:response.data[i].name})
                                    }
                                }
                                jaxspot.bus.local.topic('/event/facebook/friends/loaded').publish(fb_friends);
                            }
                        );
                    } else if (response.status === 'not_authorized') {
                        jaxspot.facebook.login();
                    } else {
                        jaxspot.facebook.login();
                    }
                });
            },
            postChallenge:function (friend, me, challenge, callback) {
                var post = {
                    method:'feed',
                    to:friend.fb_id,
                    link:'http://www.' + options.domain,
                    picture:options.ws.resource + '/media/vs.jpg',
                    name:jaxspot.i18n.message('facebook.challenge.name') + jaxspot.game.get(challenge.game).name,
                    caption:jaxspot.i18n.message('facebook.challenge.caption'),
                    description:jaxspot.i18n.message('facebook.challenge.description')
                };

                var request = {
                    method:'apprequests',
                    message:jaxspot.i18n.message('facebook.challenge.message'),
                    to:friend.fb_id
                };

                if (friend.id) {
                    var number = Math.floor((Math.random() * 10) + 1);
                    if (number < 7) {
                        FB.ui(request, callback);
                    } else {
                        FB.ui(post, callback);
                    }
                } else {
                    FB.ui(post, callback);
                }
            }
        };

        window.fbAsyncInit = function () {
            FB.init({
                appId:options.fb.appId,
                status:true,
                cookie:true,
                xfbml:true,
                oauth:true,
                frictionlessRequests:true,
                useCachedDialogs:false,
                logging:!!options.debug
            });
            jaxspot.bus.local.topic('/event/facebook/loaded').publish();
        };

        jaxspot.bus.local.topic('/event/bootstrap/loaded').subscribe(function (data) {
            if (!window.FB) {
                var locale = data.me.locale;
                logger.debug('Loading Facebook script for locale ', locale);
                // see how we cannot i18n-ize this
                $.getScript('//connect.facebook.net/en_US/all.js');
            }
        });

    })(jQuery);
}
