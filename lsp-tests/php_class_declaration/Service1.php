<?php

namespace App;

class Service1
{
    public function innerClass()
    {
        class InnerClass
        {
            public function number()
            {
                return 10;
            }
        }

        $cls = new InnerClass;

        return $cls->number();
    }
}
