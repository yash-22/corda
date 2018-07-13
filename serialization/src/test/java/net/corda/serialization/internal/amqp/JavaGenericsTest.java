package net.corda.serialization.internal.amqp;

import net.corda.core.concurrent.CordaFuture;
import net.corda.core.serialization.ClassWhitelist;
import net.corda.core.serialization.SerializedBytes;
import net.corda.serialization.internal.amqp.custom.BigDecimalSerializer;
import net.corda.serialization.internal.amqp.testutils.TestSerializationContext;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.NotSerializableException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static net.corda.serialization.internal.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;
import static org.jgroups.util.Util.assertEquals;
import static org.jgroups.util.Util.assertNotNull;
import static org.jgroups.util.Util.assertTrue;

public class JavaGenericsTest {
    private static class Inner {
        private final Integer v;

        private Inner(Integer v) {
            this.v = v;
        }

        Integer getV() {
            return v;
        }
    }

    private static class A<T> {
        private final T t;

        private A(T t) {
            this.t = t;
        }

        public T getT() {
            return t;
        }
    }

    @Test
    public void basicGeneric() throws NotSerializableException {
        A a1 = new A<>(1);

        SerializerFactory factory = testDefaultFactory();

        SerializationOutput ser = new SerializationOutput(factory);
        SerializedBytes<?> bytes = ser.serialize(a1, TestSerializationContext.testSerializationContext);

        DeserializationInput des = new DeserializationInput(factory);
        A a2 = des.deserialize(bytes, A.class, TestSerializationContext.testSerializationContext);

        assertEquals(1, a2.getT());
    }

    private SerializedBytes<?> forceWildcardSerialize(A<?> a) throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        return (new SerializationOutput(factory)).serialize(a, TestSerializationContext.testSerializationContext);
    }

    private SerializedBytes<?> forceWildcardSerializeFactory(
            A<?> a,
            SerializerFactory factory) throws NotSerializableException {
        return (new SerializationOutput(factory)).serialize(a, TestSerializationContext.testSerializationContext);
    }

    private A<?> forceWildcardDeserialize(SerializedBytes<?> bytes) throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        DeserializationInput des = new DeserializationInput(factory);
        return des.deserialize(bytes, A.class, TestSerializationContext.testSerializationContext);
    }

    private A<?> forceWildcardDeserializeFactory(
            SerializedBytes<?> bytes,
            SerializerFactory factory) throws NotSerializableException {
        return (new DeserializationInput(factory)).deserialize(bytes, A.class,
                TestSerializationContext.testSerializationContext);
    }

    @Test
    public void forceWildcard() throws NotSerializableException {
        SerializedBytes<?> bytes = forceWildcardSerialize(new A<>(new Inner(29)));
        Inner i = (Inner) forceWildcardDeserialize(bytes).getT();
        assertEquals(29, i.getV());
    }

    @Test
    public void forceWildcardSharedFactory() throws NotSerializableException {
        SerializerFactory factory = testDefaultFactory();

        SerializedBytes<?> bytes = forceWildcardSerializeFactory(new A<>(new Inner(29)), factory);
        Inner i = (Inner) forceWildcardDeserializeFactory(bytes, factory).getT();

        assertEquals(29, i.getV());
    }

    static class Things<T> {
        private List<T> things;

        List<T> getThings() {
            return this.things;
        }

        void setThings(List<T> things) {
            this.things = things;
        }
    }

    @Test
    public void roundTripThings() throws NotSerializableException {
        SerializerFactory factory1 = testDefaultFactory();
        SerializerFactory factory2 = testDefaultFactory();

        List<String> thing1list = new ArrayList<String>();
        thing1list.add("this");
        thing1list.add("is");
        thing1list.add("a");
        thing1list.add("test");

        List<String> thing2list = new ArrayList<String>();
        thing1list.add("my");
        thing1list.add("knickers");
        thing1list.add("are");
        thing1list.add("cute");

        Things<String> thing1 = new Things<>();
        thing1.setThings(thing1list);

        Things<String> thing2 = new Things<>();
        thing2.setThings(thing2list);

        List<Things> list = new ArrayList<>();
        list.add(thing1);
        list.add(thing2);

        SerializedBytes<?> bytes = new SerializationOutput(factory1)
                .serialize(list, TestSerializationContext.testSerializationContext);

        new DeserializationInput(factory1).deserialize(
                bytes,
                List.class,
                TestSerializationContext.testSerializationContext);

        new DeserializationInput(factory2).deserialize(
                bytes,
                List.class,
                TestSerializationContext.testSerializationContext);
    }

    static class Potato {
        private final Integer a;
        private final BigDecimal b;
        private final List<String> c;
        private final List<InnerPotato> d;

        public Potato(Integer a, BigDecimal b, List<String> c, List<InnerPotato> d) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }

        public Integer getA() {
            return this.a;
        }
        public BigDecimal getB() {
            return this.b;
        }
        public List<String> getC() {
            return this.c;
        }
        public List<InnerPotato> getD() {
            return this.d;
        }
    }

    static class InnerPotato {
        private final String a;
        private final String b;

        public InnerPotato(String a, String b) {
            this.a = a;
            this.b = b;
        }

        public String getA() {
            return this.a;
        }
        public String getB() {
            return this.b;
        }
    }

    private Potato makePotato() {
        InnerPotato ip1 = new InnerPotato("1", "2");
        InnerPotato ip2 = new InnerPotato("3", "4");
        List<InnerPotato> potato1list = new ArrayList<>();
        potato1list.add(ip1);
        potato1list.add(ip2);
        List<String> potato1stringlist = new ArrayList<>();
        potato1stringlist.add("a");
        potato1stringlist.add("b");
        potato1stringlist.add("c");

        return new Potato(1, BigDecimal.TEN, potato1stringlist, potato1list);
    }

    @Test
    public void testPotato() throws NotSerializableException {
        SerializerFactory factory1 = testDefaultFactory();
        SerializerFactory factory2 = testDefaultFactory();

        factory1.register(BigDecimalSerializer.INSTANCE);
        factory2.register(BigDecimalSerializer.INSTANCE);

        SerializedBytes<?> bytes = new SerializationOutput(factory1)
                .serialize(makePotato(), TestSerializationContext.testSerializationContext);

        new DeserializationInput(factory1).deserialize(
                bytes,
                Potato.class,
                TestSerializationContext.testSerializationContext);

        new DeserializationInput(factory2).deserialize(
                bytes,
                Potato.class,
                TestSerializationContext.testSerializationContext);
    }

    @Test
    public void testListPotato() throws NotSerializableException {
        SerializerFactory factory1 = testDefaultFactory();
        SerializerFactory factory2 = testDefaultFactory();

        factory1.register(BigDecimalSerializer.INSTANCE);
        factory2.register(BigDecimalSerializer.INSTANCE);

        List<Potato> potatoList = new ArrayList<>();
        potatoList.add(makePotato());
        potatoList.add(makePotato());
        potatoList.add(makePotato());

        SerializedBytes<?> bytes = new SerializationOutput(factory1)
                .serialize(potatoList, TestSerializationContext.testSerializationContext);

        @SuppressWarnings("unchecked")
        List<Potato> lp = new DeserializationInput(factory1).deserialize(
                bytes,
                List.class,
                TestSerializationContext.testSerializationContext);

        assertEquals(potatoList.get(0).a, lp.get(0).a);

        new DeserializationInput(factory2).deserialize(
                bytes,
                List.class,
                TestSerializationContext.testSerializationContext);
    }

    Object localSerialize(List<?> l, SerializerFactory f1, SerializerFactory f2) throws NotSerializableException {
        SerializedBytes<?> bytes = new SerializationOutput(f1)
                .serialize(l, TestSerializationContext.testSerializationContext);

        return new DeserializationInput(f2).deserialize(
                bytes,
                Object.class,
                TestSerializationContext.testSerializationContext);
    }

    @Test
    public void anotherPotatoTest() throws NotSerializableException {
        SerializerFactory factory1 = testDefaultFactory();
        SerializerFactory factory2 = testDefaultFactory();

        factory1.register(BigDecimalSerializer.INSTANCE);
        factory2.register(BigDecimalSerializer.INSTANCE);

        List<Potato> potatoList = new ArrayList<>();
        potatoList.add(makePotato());
        potatoList.add(makePotato());
        potatoList.add(makePotato());

        Object blob = localSerialize(potatoList, factory1, factory2);
        List<Potato> potatoList2 = (List<Potato>)blob;

        assertEquals(potatoList.get(0).a, potatoList2.get(0).a);
    }

    static class ListList<T> {
        private List<List<T>> listList;

        public List<List<T>> getListList() { return this.listList; }
        public void setListList(List<List<T>> listList) { this.listList = listList; }
    }

    static class WhiteList implements ClassWhitelist {
        @Override
        public boolean hasListed(@NotNull Class<?> type) {
            System.out.println ("listed: " + type.getSimpleName());
            return type == ListList.class || type == Object.class || type == Potato.class || type == InnerPotato.class;
        }
    }

    @Test
    public void testListPotatoNoWhitelist() throws NotSerializableException {
        SerializerFactory factory1 = testDefaultFactory();

        SerializerFactory factory2 = new SerializerFactory(
                new WhiteList(),
                ClassLoader.getSystemClassLoader(),
                false,
                new EvolutionSerializerGetter(),
                new SerializerFingerPrinter(),
                false);

        factory1.register(BigDecimalSerializer.INSTANCE);
        factory2.register(BigDecimalSerializer.INSTANCE);

        List<Potato> potatoList = new ArrayList<>();
        potatoList.add(makePotato());
        potatoList.add(makePotato());
        potatoList.add(makePotato());
        List<List<Potato>> potatoListList = new ArrayList<>();
        potatoListList.add(potatoList);

        ListList<Potato> llp = new ListList<>();
        llp.setListList(potatoListList);

        SerializedBytes<?> bytes = new SerializationOutput(factory1)
                .serialize(llp, TestSerializationContext.testSerializationContext);

        System.out.println ("\n\n-------------------\n\n");

        @SuppressWarnings("unchecked")
        ListList<Potato> lp = new DeserializationInput(factory2).deserialize(
                bytes,
                ListList.class,
                TestSerializationContext.testSerializationContext);

        assertNotNull(lp);
        List<List<Potato>> l_l_p = lp.getListList();
        assertEquals(1, l_l_p.size());
        List<Potato> l_p = l_l_p.get(0);
        assertEquals(3, l_p.size());
        assertEquals(2, l_p.get(0).d.size());
        assertEquals("1", l_p.get(0).d.get(0).a);
        assertEquals("2", l_p.get(0).d.get(0).b);
        assertEquals("3", l_p.get(0).d.get(1).a);
        assertEquals("4", l_p.get(0).d.get(1).b);
    }

    static class Apple<A, B, C> {
        private A a;
        private B b;
        private C c;
        private List<A> d;

        Apple(A a, B b, C c, List<A> d) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }

        Apple() {

        }

        public A getA() {
            return a;
        }

        public B getB() {
            return b;
        }

        public C getC() {
            return c;
        }

        public List<A> getD() {
            return d;
        }

        public List<?> getList() {
            return d;
        }

        public void setA(A a) {
            this.a = a;
        }

        public void setB(B b) {
            this.b = b;
        }

        public void setC(C c) {
            this.c = c;
        }

        public void setD(List<A> d) {
            this.d = d;
        }
    }

    private void setAppleList(List<?> l, Apple a) {
        a.setD(l);
    }

    private SerializedBytes<?> forceWildcardSerializeApple(Apple<?, ?, ?> a) throws NotSerializableException {
        return (new SerializationOutput(testDefaultFactory()))
                .serialize(a, TestSerializationContext.testSerializationContext);
    }

    private <T> Apple<?, ?, ?> makeApple(T val) {
        @SuppressWarnings("unchecked")
        Apple<T, InnerPotato, List<Integer>> innerA = new Apple(
                val,
                new InnerPotato("inner", "potato"),
                Collections.singletonList(1),
                Collections.singletonList("hello"));

        List<Apple> appleList1 = new ArrayList<>();
        appleList1.add(innerA);

        Apple<String, InnerPotato, List<Apple>> a = new Apple();

        a.setA((String)val);
        a.setB(new InnerPotato("inner", "potato"));
        a.setC(appleList1);
        a.setD(Arrays.asList("1", "2", "3"));

        return a;
    }

    @Test
    public void testWildApple() throws NotSerializableException {
        Apple<?, ?, ?> a = makeApple("wibble");

        SerializedBytes<?> blobby = forceWildcardSerializeApple(a);

        Apple<?, ?, ?> a2 = new DeserializationInput(testDefaultFactory()).deserialize(
                blobby,
                Apple.class,
                TestSerializationContext.testSerializationContext);

        SerializedBytes<?> blobby2 = forceWildcardSerializeApple(a2);

        Apple<?, ?, ?> a3 = new DeserializationInput(testDefaultFactory()).deserialize(
                blobby2,
                Apple.class,
                TestSerializationContext.testSerializationContext);

        assertEquals("wibble", a3.getA());
        assertEquals("inner", ((InnerPotato)a3.getB()).getA());
        assertEquals("potato", ((InnerPotato)a3.getB()).getB());
    }

    @Test
    public void testApple() throws NotSerializableException {
        Apple a = makeApple("dribble");

        SerializerFactory factory1 = testDefaultFactory();

        SerializedBytes<?> bytes = new SerializationOutput(factory1)
                .serialize(a, TestSerializationContext.testSerializationContext);

        System.out.println ("\n\n-------------------\n\n");

        @SuppressWarnings("unchecked")
        Apple<String, InnerPotato, List<Apple>> newApple = new DeserializationInput(testDefaultFactory()).deserialize(
                bytes,
                Apple.class,
                TestSerializationContext.testSerializationContext);

        assertEquals(1, newApple.c.size());
    }






}
