<?php

namespace App;

class Car extends Base implements Drivable
{
    public static function make(Base $base): Car
    {
        if ($base instanceof Car) {
            return self::build();
        }

        return static::build(new Car);
    }
}
