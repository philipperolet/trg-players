// Compiled by ClojureScript 1.10.520 {}
goog.provide('claby.core');
goog.require('cljs.core');
goog.require('goog.dom');
goog.require('reagent.core');
cljs.core.println.call(null,"This text is printed from src/claby/core.cljs. Go ahead and edit it and see reloading in action.");
claby.core.multiply = (function claby$core$multiply(a,b){
return (a * b);
});
if((typeof claby !== 'undefined') && (typeof claby.core !== 'undefined') && (typeof claby.core.app_state !== 'undefined')){
} else {
claby.core.app_state = reagent.core.atom.call(null,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"text","text",-1790561697),"Hello world!"], null));
}
claby.core.get_app_element = (function claby$core$get_app_element(){
return goog.dom.getElement("app");
});
claby.core.hello_world = (function claby$core$hello_world(){
return new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"div","div",1057191632),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"h1","h1",-1896887462),new cljs.core.Keyword(null,"text","text",-1790561697).cljs$core$IFn$_invoke$arity$1(cljs.core.deref.call(null,claby.core.app_state))], null),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"h3","h3",2067611163),"Claby world."], null)], null);
});
claby.core.mount = (function claby$core$mount(el){
return reagent.core.render_component.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [claby.core.hello_world], null),el);
});
claby.core.mount_app_element = (function claby$core$mount_app_element(){
var temp__5457__auto__ = claby.core.get_app_element.call(null);
if(cljs.core.truth_(temp__5457__auto__)){
var el = temp__5457__auto__;
return claby.core.mount.call(null,el);
} else {
return null;
}
});
claby.core.mount_app_element.call(null);
claby.core.on_reload = (function claby$core$on_reload(){
return claby.core.mount_app_element.call(null);
});

//# sourceMappingURL=core.js.map
