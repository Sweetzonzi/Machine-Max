package io.github.tt432.machinemax;

import io.github.tt432.machinemax.common.vehicle.signal.BooleanSignal;
import io.github.tt432.machinemax.common.vehicle.signal.EmptySignal;
import io.github.tt432.machinemax.common.vehicle.signal.Signal;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Test {
}
class Person {
    private String name;
    private int age;

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAge(int age) {
        this.age = age;
    }
}

class Main {
    public static void main(String[] args) {
        Map<String, Signal<?>> input = new HashMap<>();
        input.put("key", new BooleanSignal(true));
        Map<String, Signal<?>> output = new HashMap<>();
        output.put("key", input.get("key"));
        System.out.println(output.get("key").value);
        input.put("key", new EmptySignal());
        System.out.println(output.get("key").value);
    }
}